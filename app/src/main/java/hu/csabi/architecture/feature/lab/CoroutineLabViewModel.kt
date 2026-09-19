package hu.csabi.architecture.feature.lab

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.csabi.architecture.core.coroutines.AppDispatchers
import hu.csabi.architecture.core.coroutines.DefaultAppDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lesson 02 — `viewModelScope` is the entry point of structured concurrency on Android.
 *
 * It is a `SupervisorJob` plus `Dispatchers.Main.immediate`, cancelled automatically by
 * `onCleared()`. That is what keeps work from leaking when the user navigates away.
 *
 * State handling here is deliberately primitive — StateFlow arrives in lesson 03, a proper
 * UiState in lesson 07.
 */
class CoroutineLabViewModel(
    private val dispatchers: AppDispatchers = DefaultAppDispatchers,
) : ViewModel() {

    val logLines = mutableStateListOf<String>()

    var runningDemo by mutableStateOf<String?>(null)
        private set

    private var currentJob: Job? = null
    private var startedAt = 0L

    private val lab = CoroutineLab(dispatchers) { line -> appendLine(line) }

    /**
     * The log is Compose state, which is only safe to touch from the main thread, while the
     * demos also run on IO/Default — hence the explicit switch back.
     */
    private suspend fun appendLine(line: String) = withContext(dispatchers.main) {
        val elapsed = System.currentTimeMillis() - startedAt
        logLines += "%4d ms | %s".format(elapsed, line)
    }

    fun run(demo: Demo) {
        currentJob?.cancel()
        logLines.clear()
        startedAt = System.currentTimeMillis()
        runningDemo = demo.title

        // This launch becomes a CHILD of viewModelScope: cancelling the scope cancels it.
        currentJob = viewModelScope.launch {
            try {
                when (demo) {
                    Demo.SequentialVsParallel -> lab.sequentialVsParallel()
                    Demo.Cancellation -> lab.cooperativeCancellation()
                    Demo.Cleanup -> lab.cancellationCleanup()
                    Demo.AllOrNothing -> lab.allOrNothing()
                    Demo.Independent -> lab.independentChildren()
                    Demo.Timeout -> lab.timeout()
                    Demo.Dispatchers -> lab.dispatchers()
                }
                appendLine("── done ──")
            } catch (cancellation: CancellationException) {
                // CancellationException is normal control flow, not a failure.
                // A cancelled context cannot suspend, so this needs NonCancellable.
                withContext(NonCancellable) { appendLine("── cancelled ──") }
                throw cancellation
            } catch (throwable: Throwable) {
                appendLine("── error: ${throwable.message} ──")
            } finally {
                runningDemo = null
            }
        }
    }

    /** `cancel()` only SIGNALS cancellation; the coroutine stops at its next check point. */
    fun cancel() {
        currentJob?.cancel(CancellationException("cancelled by user"))
    }

    enum class Demo(val title: String) {
        SequentialVsParallel("1. Sequential vs. async"),
        Cancellation("2. Cooperative cancellation"),
        Cleanup("3. Cleanup + NonCancellable"),
        AllOrNothing("4. coroutineScope: all or nothing"),
        Independent("5. supervisorScope: independent children"),
        Timeout("6. withTimeout"),
        Dispatchers("7. Dispatcher switching"),
    }
}
