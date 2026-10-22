package hu.csabi.architecture.feature.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hu.csabi.architecture.core.error.AppError
import hu.csabi.architecture.core.result.AppResult
import hu.csabi.architecture.core.result.map
import hu.csabi.architecture.domain.model.Repo
import hu.csabi.architecture.domain.model.Username
import hu.csabi.architecture.domain.repository.RepoRepository
import hu.csabi.architecture.domain.usecase.SearchRepositoriesUseCase
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Lesson 04 — a bench for the error mapping.
 *
 * Lesson 07 moved search-as-you-type into its own feature screen, so what is left here is
 * the part this lab was actually for: firing specific failures on demand and seeing which
 * [AppError] each one becomes.
 *
 * Note that it still builds its UI state as a `sealed interface`. That is the right shape
 * *here*, because these states genuinely exclude each other — unlike a real screen, which
 * needs "content plus error" and therefore gets a data class (see `RepoSearchUiState`).
 */
@HiltViewModel
class NetworkLabViewModel @Inject constructor(
    private val searchRepositories: SearchRepositoriesUseCase,
    private val repository: RepoRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<NetworkUiState>(NetworkUiState.Idle)
    val state: StateFlow<NetworkUiState> = _state.asStateFlow()

    /** Deliberate failures, so every branch of the error mapping can be seen working. */
    fun trigger(scenario: Scenario) {
        viewModelScope.launch {
            _state.value = NetworkUiState.Loading
            val result = when (scenario) {
                Scenario.Valid -> repository.details(Username("square"), "retrofit")
                    .map { listOf(it) }

                Scenario.NotFound ->
                    repository.details(Username("android"), "this-repo-does-not-exist-42")
                        .map { listOf(it) }

                // Rejected by the use case before a request is ever made.
                Scenario.TooShort -> searchRepositories("a")
            }
            _state.value = result.toUiState()
        }
    }

    enum class Scenario(val title: String) {
        Valid("200 OK"),
        NotFound("404"),
        TooShort("Invalid input"),
    }
}

/**
 * Turning the typed error into something a human can act on. Note how each branch says
 * something different — this is the reason the error hierarchy exists at all.
 *
 * Lesson 05 narrowed Failure to AppError, so this `when` is exhaustive without an `else`:
 * adding a new error case breaks the build here until it is handled.
 */
private fun AppResult<List<Repo>>.toUiState(): NetworkUiState = when (this) {
    is AppResult.Success -> if (data.isEmpty()) {
        NetworkUiState.Empty
    } else {
        NetworkUiState.Content(data)
    }

    is AppResult.Failure -> when (val error = this.error) {
        is AppError.Network ->
            NetworkUiState.Failed("No connection. Check your network and try again.", retryable = true)

        is AppError.RateLimited -> NetworkUiState.Failed(
            "Rate limit reached${error.resetAtEpochSeconds.asResetHint()}",
            retryable = false,
        )

        is AppError.Http -> when (error.code) {
            404 -> NetworkUiState.Failed("Not found (404)", retryable = false)
            422 -> NetworkUiState.Failed("The server rejected the query (422)", retryable = false)
            else -> NetworkUiState.Failed("Server error (${error.code})", retryable = error.isRetryable)
        }

        is AppError.Serialization ->
            NetworkUiState.Failed("Unexpected response format", retryable = false)

        is AppError.InvalidInput ->
            NetworkUiState.Failed(error.reason, retryable = false)

        is AppError.Unknown ->
            NetworkUiState.Failed(error.message ?: "Unknown error", retryable = false)
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
