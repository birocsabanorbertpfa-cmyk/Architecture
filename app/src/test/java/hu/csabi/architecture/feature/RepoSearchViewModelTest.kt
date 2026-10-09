package hu.csabi.architecture.feature

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import hu.csabi.architecture.R
import hu.csabi.architecture.core.error.AppError
import hu.csabi.architecture.core.ui.UiText
import hu.csabi.architecture.domain.usecase.SearchRepositoriesUseCase
import hu.csabi.architecture.feature.search.RepoSearchEvent
import hu.csabi.architecture.feature.search.RepoSearchViewModel
import hu.csabi.architecture.testing.FakeRepoRepository
import hu.csabi.architecture.testing.MainDispatcherRule
import hu.csabi.architecture.testing.repo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * Lesson 10 — testing a ViewModel without a device, a clock or a sleep.
 *
 * Three tools, each solving a specific problem:
 *  - [MainDispatcherRule] gives `viewModelScope` a dispatcher on the JVM
 *  - `runTest` provides a **virtual clock**, so a 350 ms debounce costs no real time
 *  - Turbine's `test {}` collects a flow and lets the test assert item by item
 *
 * The last one matters more than it looks: `uiState` is a `stateIn(WhileSubscribed)` flow,
 * which produces **nothing at all** until something collects it. A test that just reads
 * `uiState.value` would see the initial value forever and pass while asserting nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RepoSearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeRepoRepository()
    private val useCase = SearchRepositoriesUseCase(repository)

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) =
        RepoSearchViewModel(useCase, savedState)

    @Test
    fun `initial state is empty and asks for nothing`() = runTest {
        val viewModel = viewModel()

        viewModel.uiState.test {
            val initial = awaitItem()
            assertThat(initial.query).isEmpty()
            assertThat(initial.repos).isEmpty()
            assertThat(initial.isLoading).isFalse()
            assertThat(initial.showPrompt).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repository.refreshCount).isEqualTo(0)
    }

    @Test
    fun `typing updates the query immediately but does not search until the debounce passes`() =
        runTest {
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitItem() // initial

                viewModel.onEvent(RepoSearchEvent.QueryChanged("okhttp"))
                runCurrent()

                // The query echoes straight away: that is the `combine` of input and outcome,
                // and it is why the text field feels instant.
                assertThat(awaitItem().query).isEqualTo("okhttp")
                assertThat(repository.refreshCount).isEqualTo(0)

                // Still nothing at 300 ms — the debounce is 350.
                advanceTimeBy(300)
                assertThat(repository.refreshCount).isEqualTo(0)

                advanceTimeBy(100)
                assertThat(repository.refreshCount).isEqualTo(1)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `fast typing collapses into a single search for the last query`() = runTest {
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()

            // Each keystroke arrives well inside the debounce window.
            listOf("o", "ok", "okh", "okht", "okhtt", "okhttp").forEach { text ->
                viewModel.onEvent(RepoSearchEvent.QueryChanged(text))
                advanceTimeBy(50)
            }
            advanceTimeBy(400)

            // `debounce` plus `distinctUntilChanged` mean one request, for the final text.
            assertThat(repository.refreshCount).isEqualTo(1)
            assertThat(repository.lastQuery?.raw).isEqualTo("okhttp")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a successful search shows the rows that storage reports`() = runTest {
        repository.remoteRepos = listOf(repo(id = 1, name = "okhttp", stars = 46_000))
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onEvent(RepoSearchEvent.QueryChanged("okhttp"))
            advanceTimeBy(500)
            runCurrent()

            val state = expectMostRecentItem()
            assertThat(state.isLoading).isFalse()
            assertThat(state.repos.map { it.name }).containsExactly("okhttp")
            assertThat(state.error).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed refresh keeps cached rows visible and shows an error over them`() = runTest {
        // The scenario offline-first exists for: there is cached data, the network is gone.
        repository.seed(listOf(repo(id = 1, name = "okhttp")))
        repository.failNextRefresh(AppError.Network(cause = null))
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onEvent(RepoSearchEvent.QueryChanged("okhttp"))
            advanceTimeBy(500)
            runCurrent()

            val state = expectMostRecentItem()
            assertThat(state.repos.map { it.name }).containsExactly("okhttp")
            assertThat(state.error).isEqualTo(UiText.Resource(R.string.error_network))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the error maps to a resource id, not a sentence`() = runTest {
        repository.failNextRefresh(AppError.RateLimited(resetAtEpochSeconds = null))
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onEvent(RepoSearchEvent.QueryChanged("okhttp"))
            advanceTimeBy(500)
            runCurrent()

            // Asserting the resource id is exact; asserting English prose would break on the
            // next copy edit. This is the practical reason UiText exists.
            assertThat(expectMostRecentItem().error)
                .isEqualTo(UiText.Resource(R.string.error_rate_limited))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dismissing the error clears it without re-running the search`() = runTest {
        repository.failNextRefresh(AppError.Network(cause = null))
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onEvent(RepoSearchEvent.QueryChanged("okhttp"))
            advanceTimeBy(500)
            runCurrent()
            assertThat(expectMostRecentItem().error).isNotNull()

            val refreshesBefore = repository.refreshCount
            viewModel.onEvent(RepoSearchEvent.ErrorDismissed)
            runCurrent()

            assertThat(expectMostRecentItem().error).isNull()
            assertThat(repository.refreshCount).isEqualTo(refreshesBefore)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retry runs the same query again and forces past the freshness check`() = runTest {
        repository.failNextRefresh(AppError.Network(cause = null))
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onEvent(RepoSearchEvent.QueryChanged("okhttp"))
            advanceTimeBy(500)
            runCurrent()

            repository.nextRefreshResult = hu.csabi.architecture.core.result.AppResult.Success(Unit)
            repository.remoteRepos = listOf(repo(id = 2, name = "okhttp"))

            viewModel.onEvent(RepoSearchEvent.Retry)
            advanceTimeBy(500)
            runCurrent()

            assertThat(repository.refreshCount).isEqualTo(2)
            assertThat(repository.lastForce).isTrue()
            assertThat(expectMostRecentItem().error).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the query is restored from saved state, so process death does not lose it`() = runTest {
        // Exactly what SavedStateHandle buys over a plain MutableStateFlow.
        val viewModel = viewModel(SavedStateHandle(mapOf("query" to "retrofit")))

        viewModel.uiState.test {
            // The first item is `stateIn`'s `initialValue`, which is emitted before the
            // reducer has seen anything — a detail worth asserting rather than skipping,
            // because a screen does render that frame.
            assertThat(awaitItem().query).isEmpty()

            // The second is the first reduction, carrying the restored query.
            assertThat(awaitItem().query).isEqualTo("retrofit")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clicking a repo emits an effect rather than changing state`() = runTest {
        val clicked = repo(id = 9, name = "coil")
        val viewModel = viewModel()

        viewModel.effects.test {
            viewModel.onEvent(RepoSearchEvent.RepoClicked(clicked))
            runCurrent()

            val effect = awaitItem()
            assertThat(effect).isInstanceOf(
                hu.csabi.architecture.feature.search.RepoSearchEffect.OpenRepo::class.java,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an effect emitted with no collector is not lost`() = runTest {
        // The Channel-over-SharedFlow decision from lesson 07, asserted: the event is sent
        // before anyone is listening, and still arrives. A SharedFlow would have dropped it.
        val viewModel = viewModel()
        viewModel.onEvent(RepoSearchEvent.RepoClicked(repo(id = 3)))
        runCurrent()

        viewModel.effects.test {
            assertThat(awaitItem()).isNotNull()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
