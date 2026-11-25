package hu.csabi.architecture.feature.async

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hu.csabi.architecture.core.connectivity.NetworkMonitor
import hu.csabi.architecture.core.coroutines.restartWhen
import hu.csabi.architecture.core.coroutines.retryWithBackoff
import hu.csabi.architecture.core.coroutines.throttleFirst
import hu.csabi.architecture.core.error.AppError
import hu.csabi.architecture.domain.sync.RepoSyncScheduler
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Lesson 13 — the new async machinery, visible on screen.
 */
@HiltViewModel
class AsyncLabViewModel @Inject constructor(
    networkMonitor: NetworkMonitor,
    private val syncScheduler: RepoSyncScheduler,
) : ViewModel() {

    /**
     * The `callbackFlow` from `:data`, turned into screen state. `stateIn` is what makes one
     * `registerNetworkCallback` serve every observer — collecting the cold flow twice would
     * register the callback twice.
     */
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = true)

    val isSyncing: StateFlow<Boolean> = syncScheduler.isSyncing
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = false)

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    /** Taps, so `throttleFirst` has something to throttle. */
    private val taps = MutableSharedFlow<Unit>(extraBufferCapacity = 64)

    private var startedAt = System.currentTimeMillis()

    init {
        /**
         * `throttleFirst` on a button: the first tap acts, the rest of the burst is dropped.
         * `debounce` here would make every tap feel delayed, which is the usual mix-up.
         */
        viewModelScope.launch {
            taps.throttleFirst(windowMillis = 1_000)
                .onEach { append("accepted tap (next one allowed in 1000 ms)") }
                .collect()
        }
    }

    fun onTap() {
        taps.tryEmit(Unit)
    }

    /**
     * A flow that fails twice and then succeeds, run through `retryWithBackoff`. The log
     * shows the growing gaps — and because of jitter, no two runs are identical.
     */
    fun runBackoffDemo() {
        reset("retryWithBackoff: fails twice, then succeeds")
        viewModelScope.launch {
            var attempt = 0
            flow {
                attempt++
                append("attempt #$attempt")
                if (attempt < 3) throw IOException("connection reset")
                emit("payload")
            }
                .retryWithBackoff(
                    maxAttempts = 4,
                    initialDelayMillis = 300,
                    // Only transient failures are worth repeating. The same rule the data
                    // layer already encodes, reused rather than restated.
                    shouldRetry = { it !is AppError || it.isRetryable },
                )
                .onEach { append("collected $it") }
                .collect()
            append("── done ──")
        }
    }

    /**
     * `restartWhen`: the upstream is cancelled while offline and starts over when the
     * connection returns. No `if (isOnline)` anywhere — the condition *is* a flow.
     */
    fun runRestartDemo() {
        reset("restartWhen(isOnline): toggle airplane mode to see it restart")
        viewModelScope.launch {
            var run = 0
            flow {
                run++
                append("upstream started (run #$run)")
                emit(run)
            }
                .restartWhen(isOnline)
                .onEach { append("received run #$it") }
                .collect()
        }
    }

    fun enqueueSync() {
        append("enqueued one-time sync (WorkManager decides when)")
        syncScheduler.syncNow(query = "kotlin")
    }

    private fun reset(title: String) {
        startedAt = System.currentTimeMillis()
        _log.value = listOf(title)
    }

    private fun append(line: String) {
        val elapsed = System.currentTimeMillis() - startedAt
        _log.value = _log.value + "%5d ms | %s".format(elapsed, line)
    }
}

/** `collect()` with no action drains a flow, running its side effects. */
private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.collect() = collect { }
