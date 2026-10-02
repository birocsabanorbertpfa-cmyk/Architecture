package hu.csabi.architecture.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hu.csabi.architecture.R
import hu.csabi.architecture.core.error.AppError
import hu.csabi.architecture.core.result.AppResult
import hu.csabi.architecture.core.ui.UiText
import hu.csabi.architecture.domain.model.Repo
import hu.csabi.architecture.domain.usecase.SearchRepositoriesUseCase
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Lesson 07 — one entry point in, one state out.
 *
 * The public surface is [onEvent], [uiState] and [effects]. Nothing else, so a screen has no
 * way to put the ViewModel into an inconsistent state.
 *
 * The state is produced by a **reducer**: every input — a keystroke, a search outcome, a
 * dismissal — is turned into a [Change], and `scan` folds those changes over the previous
 * state. That is what makes "keep the old list while refreshing" or "show a list and an
 * error banner" expressible without a tangle of nullable fields being set from three places.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class RepoSearchViewModel @Inject constructor(
    private val searchRepositories: SearchRepositoriesUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /**
     * `SavedStateHandle` rather than a plain `MutableStateFlow`: this survives **process
     * death**, not only configuration changes. Background the app, let the system kill the
     * process, come back — the query is still there. `viewModelScope` cannot do that,
     * because the whole process was gone.
     */
    private val query: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")

    /** Re-runs the current query. The counter is what makes a repeated retry distinct. */
    private val retryTrigger = MutableStateFlow(0)

    private val dismissals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val searchOutcome: Flow<Outcome> = combine(
        query.debounce(DEBOUNCE_MILLIS).map { it.trim() }.distinctUntilChanged(),
        retryTrigger,
    ) { text, _ -> text }
        .flatMapLatest { text ->
            if (text.length < RepoSearchUiState.MIN_QUERY_LENGTH) {
                flow { emit(Outcome.Idle) }
            } else {
                flow {
                    emit(Outcome.Loading)
                    emit(searchRepositories(text).toOutcome())
                }
            }
        }

    /**
     * The query is merged in separately from the search outcome on purpose: that is what
     * makes typing feel instant. Every keystroke updates the state immediately, while the
     * debounced search result arrives later and folds in on top.
     *
     * `WhileSubscribed` keeps the whole pipeline lazy — nothing runs until the screen
     * collects, and it stops five seconds after the screen leaves.
     */
    val uiState: StateFlow<RepoSearchUiState> = merge(
        query.map { Change.Query(it) },
        searchOutcome.map { Change.Search(it) },
        dismissals.map { Change.ClearError },
    )
        .scan(RepoSearchUiState()) { state, change -> state.reduce(change) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = RepoSearchUiState(),
        )

    /**
     * A `Channel` rather than a `MutableSharedFlow` for effects.
     *
     * A `SharedFlow` with no subscriber **drops** emissions, so an effect fired while the
     * screen is between lifecycle states is simply lost. A buffered channel holds it until
     * the consumer collects. The trade-off — exactly one consumer — is precisely the
     * contract a navigation command wants.
     */
    private val effectChannel = Channel<RepoSearchEffect>(Channel.BUFFERED)
    val effects: Flow<RepoSearchEffect> = effectChannel.receiveAsFlow()

    fun onEvent(event: RepoSearchEvent) {
        when (event) {
            // Writing to the handle updates `query`, which is a StateFlow over the same key.
            is RepoSearchEvent.QueryChanged -> savedStateHandle[KEY_QUERY] = event.value

            is RepoSearchEvent.RepoClicked -> viewModelScope.launch {
                effectChannel.send(RepoSearchEffect.OpenRepo(event.repo))
            }

            RepoSearchEvent.Retry -> retryTrigger.value += 1

            RepoSearchEvent.ErrorDismissed -> dismissals.tryEmit(Unit)
        }
    }

    /**
     * The reducer: a pure function from (state, change) to state. No coroutines, no
     * injection, no Android — which is why lesson 10 can test the whole screen logic by
     * calling it directly.
     */
    private fun RepoSearchUiState.reduce(change: Change): RepoSearchUiState = when (change) {
        is Change.Query -> copy(query = change.value)
        Change.ClearError -> copy(error = null)

        is Change.Search -> when (val outcome = change.outcome) {
            Outcome.Idle -> copy(repos = emptyList(), isLoading = false, error = null)
            // `repos` is deliberately untouched: the old list stays on screen while the new
            // request is in flight.
            Outcome.Loading -> copy(isLoading = true, error = null)
            is Outcome.Success -> copy(repos = outcome.repos, isLoading = false, error = null)
            is Outcome.Failure -> copy(isLoading = false, error = outcome.message)
        }
    }

    private fun AppResult<List<Repo>>.toOutcome(): Outcome = when (this) {
        is AppResult.Success -> Outcome.Success(data)
        is AppResult.Failure -> when (val cause = error) {
            // The use case rejects a too-short query; that is not worth shouting about.
            is AppError.InvalidInput -> Outcome.Idle
            is AppError.Network -> Outcome.Failure(UiText.Resource(R.string.error_network))

            is AppError.RateLimited -> Outcome.Failure(
                cause.resetAtEpochSeconds?.let { resetAt ->
                    UiText.Resource(
                        R.string.error_rate_limited_until,
                        listOf(resetAt.asClockTime()),
                    )
                } ?: UiText.Resource(R.string.error_rate_limited),
            )

            is AppError.Http -> Outcome.Failure(
                when (cause.code) {
                    HTTP_NOT_FOUND -> UiText.Resource(R.string.error_not_found)
                    else -> UiText.Resource(R.string.error_server, listOf(cause.code))
                },
            )

            is AppError.Serialization ->
                Outcome.Failure(UiText.Resource(R.string.error_response_format))

            is AppError.Unknown -> Outcome.Failure(UiText.Resource(R.string.error_unknown))
        }
    }

    /** Inputs to the reducer. Internal: how the state is built is nobody else's business. */
    private sealed interface Change {
        data class Query(val value: String) : Change
        data class Search(val outcome: Outcome) : Change
        data object ClearError : Change
    }

    private sealed interface Outcome {
        data object Idle : Outcome
        data object Loading : Outcome
        data class Success(val repos: List<Repo>) : Outcome
        data class Failure(val message: UiText) : Outcome
    }

    private companion object {
        const val KEY_QUERY = "query"
        const val DEBOUNCE_MILLIS = 350L
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val HTTP_NOT_FOUND = 404
    }
}

private fun Long.asClockTime(): String = Instant.ofEpochSecond(this)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("HH:mm"))
