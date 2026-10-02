package hu.csabi.architecture.feature.search

import androidx.compose.runtime.Immutable
import hu.csabi.architecture.core.ui.UiText
import hu.csabi.architecture.domain.model.Repo

/**
 * Lesson 07 — the contract between a screen and its ViewModel, written down.
 *
 * Three types, one direction each:
 *  - [RepoSearchUiState] flows **down**: everything the screen needs to draw itself.
 *  - [RepoSearchEvent] flows **up**: everything the user can do.
 *  - [RepoSearchEffect] flows **sideways, once**: things that happen rather than things
 *    that are true.
 *
 * That is unidirectional data flow. Its practical payoff is that the composable becomes a
 * pure function of the state, so it can be previewed and asserted on without a ViewModel,
 * a network or a device.
 */
@Immutable
data class RepoSearchUiState(
    val query: String = "",
    val repos: List<Repo> = emptyList(),
    val isLoading: Boolean = false,
    val error: UiText? = null,
) {
    /**
     * Why a single data class rather than `sealed interface Loading | Content | Error`?
     *
     * Because the states are not mutually exclusive in a real screen. "Showing yesterday's
     * results while refreshing" and "a list plus an error banner" are both legitimate, and a
     * sealed hierarchy forces you to either lose the list or invent `ContentWithError`. Flags
     * on one immutable object compose freely.
     *
     * Derived values belong here too: computing them in the composable means recomputing on
     * every recomposition, and duplicating the rule in every preview.
     */
    val showPrompt: Boolean get() = query.trim().length < MIN_QUERY_LENGTH && repos.isEmpty()

    val showEmpty: Boolean get() = !isLoading && !showPrompt && error == null && repos.isEmpty()

    companion object {
        const val MIN_QUERY_LENGTH = 2
    }
}

/**
 * One event type per user action, not one callback per widget. The screen does not decide
 * what an action means — it reports what happened and the ViewModel decides.
 */
sealed interface RepoSearchEvent {
    data class QueryChanged(val value: String) : RepoSearchEvent
    data class RepoClicked(val repo: Repo) : RepoSearchEvent
    data object Retry : RepoSearchEvent
    data object ErrorDismissed : RepoSearchEvent
}

/**
 * Effects are *events*, not state: navigating, showing a snackbar, firing a haptic. The
 * difference matters because state is re-read on every recomposition — a navigation command
 * stored in state would fire again after a rotation.
 */
sealed interface RepoSearchEffect {
    data class ShowMessage(val text: UiText) : RepoSearchEffect
    data class OpenRepo(val repo: Repo) : RepoSearchEffect
}
