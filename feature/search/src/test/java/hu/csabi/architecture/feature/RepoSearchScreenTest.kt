package hu.csabi.architecture.feature

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import hu.csabi.architecture.feature.search.R
import hu.csabi.architecture.core.ui.UiText
import hu.csabi.architecture.feature.search.RepoSearchEvent
import hu.csabi.architecture.feature.search.RepoSearchScreen
import hu.csabi.architecture.feature.search.RepoSearchUiState
import hu.csabi.architecture.feature.search.SearchTestTags
import hu.csabi.architecture.testing.repo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Lesson 11 — Compose tests that run on the JVM.
 *
 * Robolectric supplies the Android runtime, so these execute in seconds with no emulator and
 * run in CI like any other unit test. The trade-off is honest: Robolectric *simulates*
 * Android, so it will not catch a GPU-specific rendering bug or a real-device timing issue.
 * The instrumented test in `androidTest` covers that end of the spectrum.
 *
 * What makes this possible at all is lesson 07's route/screen split: [RepoSearchScreen] is a
 * pure function of its state, so each test simply hands it a state and asserts what appears.
 * No ViewModel, no Hilt, no network, no coroutines.
 *
 * `sdk = [34]` because Robolectric ships a runtime per API level, and pinning one keeps the
 * tests independent of whatever `compileSdk` becomes later.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepoSearchScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<RepoSearchEvent>()

    private fun setScreen(state: RepoSearchUiState) {
        composeRule.setContent {
            RepoSearchScreen(state = state, onEvent = { events += it })
        }
    }

    @Test
    fun `the prompt is shown when the query is too short to search`() {
        setScreen(RepoSearchUiState())

        composeRule.onNodeWithText("Type at least two characters to search.").assertIsDisplayed()
    }

    @Test
    fun `results are rendered with name and star count`() {
        setScreen(
            RepoSearchUiState(
                query = "okhttp",
                repos = listOf(repo(id = 1, name = "okhttp", owner = "square", stars = 46_200)),
            ),
        )

        composeRule.onNodeWithText("square/okhttp").assertIsDisplayed()
        // The formatted value, so the Stars.formatted() rule is covered through the UI too.
        composeRule.onNodeWithText("★ 46.2k").assertIsDisplayed()
    }

    @Test
    fun `an empty result set explains itself instead of showing a blank screen`() {
        setScreen(RepoSearchUiState(query = "zzzzzz"))

        composeRule.onNodeWithText("No repository matched “zzzzzz”.").assertIsDisplayed()
    }

    @Test
    fun `a refresh shows progress while keeping the previous results on screen`() {
        // The state combination a sealed hierarchy could not express — asserted here.
        setScreen(
            RepoSearchUiState(
                query = "okhttp",
                repos = listOf(repo(id = 1, name = "okhttp", owner = "square")),
                isLoading = true,
            ),
        )

        composeRule.onNodeWithTag(SearchTestTags.PROGRESS).assertIsDisplayed()
        composeRule.onNodeWithText("square/okhttp").assertIsDisplayed()
    }

    @Test
    fun `an error is shown over the results rather than replacing them`() {
        setScreen(
            RepoSearchUiState(
                query = "okhttp",
                repos = listOf(repo(id = 1, name = "okhttp", owner = "square")),
                error = UiText.Resource(R.string.error_network),
            ),
        )

        composeRule.onNodeWithTag(SearchTestTags.ERROR_BANNER).assertIsDisplayed()
        composeRule.onNodeWithText("No connection. Check your network and try again.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("square/okhttp").assertIsDisplayed()
    }

    @Test
    fun `typing reports a QueryChanged event and changes nothing by itself`() {
        setScreen(RepoSearchUiState())

        composeRule.onNodeWithTag(SearchTestTags.QUERY_FIELD).performTextInput("okhttp")

        // Unidirectional data flow in one assertion: the screen reports, it does not decide.
        assertThat(events).containsExactly(RepoSearchEvent.QueryChanged("okhttp"))
    }

    @Test
    fun `retry and dismiss report their own events`() {
        setScreen(
            RepoSearchUiState(query = "okhttp", error = UiText.Resource(R.string.error_network)),
        )

        composeRule.onNodeWithText("Retry").performClick()
        composeRule.onNodeWithText("Dismiss").performClick()

        assertThat(events).containsExactly(RepoSearchEvent.Retry, RepoSearchEvent.ErrorDismissed)
            .inOrder()
    }

    @Test
    fun `clicking a row reports which repo was clicked`() {
        val clicked = repo(id = 7, name = "coil", owner = "coil-kt")
        setScreen(RepoSearchUiState(query = "coil", repos = listOf(clicked)))

        composeRule.onNodeWithText("coil-kt/coil").performClick()

        assertThat(events).containsExactly(RepoSearchEvent.RepoClicked(clicked))
    }
}
