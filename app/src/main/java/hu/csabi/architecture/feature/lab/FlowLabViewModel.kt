package hu.csabi.architecture.feature.lab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.csabi.architecture.core.coroutines.AppDispatchers
import hu.csabi.architecture.core.coroutines.DefaultAppDispatchers
import hu.csabi.architecture.core.model.Repo
import hu.csabi.architecture.core.model.searchQuery
import hu.csabi.architecture.data.fake.FakeRepoSearch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lesson 03 — the shape a real screen takes once state is a Flow.
 *
 * Two different jobs live here on purpose:
 *  - `searchState` is a **declarative pipeline**: user input in, screen state out. No
 *    manual job juggling, no "is a request in flight" booleans.
 *  - the demo runner below keeps the imperative style from lesson 02, for comparison.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class FlowLabViewModel(
    private val dispatchers: AppDispatchers = DefaultAppDispatchers,
) : ViewModel() {

    private val searchApi = FakeRepoSearch(failureRate = 0.2f)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /**
     * Search-as-you-type, expressed as one chain:
     *
     * - `debounce`             waits for a pause in typing instead of firing per keystroke
     * - `distinctUntilChanged` skips work when the trimmed text did not actually change
     * - `flatMapLatest`        **cancels the previous request** when a new query arrives,
     *                          which is what prevents stale results from overwriting fresh ones
     * - `catch`                turns an upstream failure into a state instead of a crash
     * - `stateIn`              converts the cold chain into hot state with a current value
     *
     * `WhileSubscribed(5_000)` keeps the upstream alive for five seconds after the last
     * collector leaves, so a rotation does not restart the request — but a real departure
     * does stop it.
     */
    val searchState: StateFlow<SearchUiState> = _query
        .debounce(300)
        .map { it.trim() }
        .distinctUntilChanged()
        .flatMapLatest { text ->
            if (text.length < MIN_QUERY_LENGTH) {
                flowOf(SearchUiState.Idle)
            } else {
                flow {
                    emit(SearchUiState.Loading)
                    val repos = searchApi.search(searchQuery { term(text) })
                    emit(
                        if (repos.isEmpty()) SearchUiState.Empty else SearchUiState.Content(repos),
                    )
                }.catch { cause ->
                    // A one-off event (snackbar) and a persistent state are different things:
                    // the event fires once, the state survives recomposition.
                    _events.tryEmit("Search failed: ${cause.message}")
                    emit(SearchUiState.Error(cause.message ?: "Unknown error"))
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SearchUiState.Idle,
        )

    /**
     * Events, not state. `MutableSharedFlow` with `replay = 0` means a subscriber that
     * arrives later does not re-receive an old message — exactly what you want for a
     * snackbar, and exactly what a StateFlow would get wrong.
     */
    private val _events = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1)
    val events: SharedFlow<String> = _events.asSharedFlow()

    fun onQueryChange(value: String) {
        _query.value = value
    }

    // ── Demo runner ─────────────────────────────────────────────────────────
    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private val _runningDemo = MutableStateFlow<String?>(null)
    val runningDemo: StateFlow<String?> = _runningDemo.asStateFlow()

    private var demoJob: Job? = null
    private var startedAt = 0L

    private val lab = FlowLab(dispatchers) { line -> appendLine(line) }

    private suspend fun appendLine(line: String) = withContext(dispatchers.main) {
        val elapsed = System.currentTimeMillis() - startedAt
        _log.value = _log.value + "%4d ms | %s".format(elapsed, line)
    }

    fun run(demo: Demo) {
        demoJob?.cancel()
        _log.value = emptyList()
        startedAt = System.currentTimeMillis()
        _runningDemo.value = demo.title

        demoJob = viewModelScope.launch {
            try {
                when (demo) {
                    Demo.Cold -> lab.coldStream()
                    Demo.Operators -> lab.operators()
                    Demo.FlowOn -> lab.contextAndFlowOn()
                    Demo.Backpressure -> lab.backpressure()
                    Demo.CombineZip -> lab.combineVsZip()
                    Demo.Errors -> lab.errorsAndRetry()
                    Demo.ColdToHot -> lab.coldToHot()
                }
                appendLine("── done ──")
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                appendLine("── error: ${throwable.message} ──")
            } finally {
                _runningDemo.value = null
            }
        }
    }

    enum class Demo(val title: String) {
        Cold("1. Cold stream"),
        Operators("2. Operators (transform)"),
        FlowOn("3. Context + flowOn"),
        Backpressure("4. buffer / conflate / collectLatest"),
        CombineZip("5. combine vs zip"),
        Errors("6. catch + retryWhen"),
        ColdToHot("7. shareIn: cold to hot"),
    }

    private companion object {
        const val MIN_QUERY_LENGTH = 2
    }
}

/**
 * Lesson 03 — a first, minimal screen state. Lesson 07 formalises this into the UiState
 * pattern used across the app; what matters here is that loading, empty, content and error
 * are *distinct states*, not booleans scattered around a data class.
 */
sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data object Empty : SearchUiState
    data class Content(val repos: List<Repo>) : SearchUiState
    data class Error(val message: String) : SearchUiState
}
