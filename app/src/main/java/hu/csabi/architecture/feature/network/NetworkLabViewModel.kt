package hu.csabi.architecture.feature.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.csabi.architecture.core.error.AppError
import hu.csabi.architecture.core.model.Repo
import hu.csabi.architecture.core.model.searchQuery
import hu.csabi.architecture.core.result.AppResult
import hu.csabi.architecture.core.result.map
import hu.csabi.architecture.data.remote.GithubRemoteDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Lesson 04 — the lesson 03 pipeline, now talking to the real GitHub API.
 *
 * The chain is unchanged; only the data source behind `flatMapLatest` is different. That is
 * the payoff of keeping the fake and the real implementation behind the same suspend
 * signature returning domain types.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class NetworkLabViewModel(
    private val remote: GithubRemoteDataSource = GithubRemoteDataSource(),
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val state: StateFlow<NetworkUiState> = _query
        .debounce(400)
        .map { it.trim() }
        .distinctUntilChanged()
        .flatMapLatest { text ->
            if (text.length < 2) {
                flowOf<NetworkUiState>(NetworkUiState.Idle)
            } else {
                flow {
                    emit(NetworkUiState.Loading)
                    // No try/catch: the data source returns failures as values.
                    emit(remote.searchRepositories(searchQuery { term(text) }).toUiState())
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NetworkUiState.Idle)

    fun onQueryChange(value: String) {
        _query.value = value
    }

    /** Deliberate failures, so every branch of the error mapping can be seen working. */
    fun trigger(scenario: Scenario) {
        viewModelScope.launch {
            _manualState.value = NetworkUiState.Loading
            val result = when (scenario) {
                Scenario.NotFound -> remote.repoDetails("android", "this-repo-does-not-exist-42")
                    .map { listOf(it) }

                Scenario.Validation -> remote.searchRepositories(searchQuery { })
                Scenario.Valid -> remote.repoDetails("square", "retrofit").map { listOf(it) }
            }
            _manualState.value = result.toUiState()
        }
    }

    private val _manualState = MutableStateFlow<NetworkUiState>(NetworkUiState.Idle)
    val manualState: StateFlow<NetworkUiState> = _manualState.asStateFlow()

    enum class Scenario(val title: String) {
        Valid("200 OK"),
        NotFound("404"),
        Validation("422"),
    }
}

/**
 * Turning the typed error into something a human can act on. Note how each branch says
 * something different — this is the reason the error hierarchy exists at all.
 */
private fun AppResult<List<Repo>>.toUiState(): NetworkUiState = when (this) {
    is AppResult.Success -> if (data.isEmpty()) {
        NetworkUiState.Empty
    } else {
        NetworkUiState.Content(data)
    }

    // `AppResult.Failure` still carries a plain `Throwable`, so the compiler cannot prove
    // this `when` is exhaustive. Lesson 05 narrows it to `Failure(AppError)` and the `else`
    // branch below disappears.
    is AppResult.Failure -> when (val error = this.error) {
        is AppError.Network ->
            NetworkUiState.Failed("No connection. Check your network and try again.", retryable = true)

        is AppError.RateLimited ->
            NetworkUiState.Failed("Rate limit reached${error.resetAtEpochSeconds.asResetHint()}", retryable = false)

        is AppError.Http -> when (error.code) {
            404 -> NetworkUiState.Failed("Not found (404)", retryable = false)
            422 -> NetworkUiState.Failed("The server rejected the query (422)", retryable = false)
            else -> NetworkUiState.Failed("Server error (${error.code})", retryable = error.isRetryable)
        }

        is AppError.Serialization ->
            NetworkUiState.Failed("Unexpected response format", retryable = false)

        is AppError.Unknown ->
            NetworkUiState.Failed(error.message ?: "Unknown error", retryable = false)

        else -> NetworkUiState.Failed(error.message ?: "Unknown error", retryable = false)
    }
}

private fun Long?.asResetHint(): String {
    if (this == null) return ""
    val time = Instant.ofEpochSecond(this)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))
    return ", resets at $time"
}

sealed interface NetworkUiState {
    data object Idle : NetworkUiState
    data object Loading : NetworkUiState
    data object Empty : NetworkUiState
    data class Content(val repos: List<Repo>) : NetworkUiState
    data class Failed(val message: String, val retryable: Boolean) : NetworkUiState
}
