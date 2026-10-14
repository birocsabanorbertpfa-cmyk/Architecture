package hu.csabi.architecture.feature

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import hu.csabi.architecture.MainActivity
import hu.csabi.architecture.feature.search.SearchTestTags
import org.junit.Rule
import org.junit.Test

/**
 * Lesson 11 — the end-to-end test, on a real device.
 *
 * Everything below the UI is production code: the real ViewModel, the real use case, the
 * real repository and real Room on real SQLite. Only the network is a fake, because that is
 * the one dependency a test cannot make deterministic.
 *
 * Rule order matters and is a classic trap: [HiltAndroidRule] has to run **before** the
 * compose rule, because the Activity it launches needs the graph to exist already. JUnit
 * applies rules bottom-up unless `order` says otherwise, hence the explicit numbers.
 *
 * This needs a device or emulator: `./gradlew :app:connectedDebugAndroidTest`.
 */
@HiltAndroidTest
class SearchEndToEndTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun typingAQueryShowsResultsFromTheWholeStack() {
        openSearchTab()

        composeRule.onNodeWithTag(SearchTestTags.QUERY_FIELD).performTextInput("okhttp")

        /**
         * `waitUntil` rather than a sleep. The query is debounced by 350 ms and then travels
         * through the use case, the fake network and Room before coming back as state, so the
         * test has to wait for a *condition*, not for a duration. A fixed delay long enough
         * to be safe on a fast machine is still too short on a loaded CI runner.
         */
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("square/okhttp").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("square/okhttp").assertIsDisplayed()
    }

    @Test
    fun resultsSurviveAConfigurationChange() {
        openSearchTab()
        composeRule.onNodeWithTag(SearchTestTags.QUERY_FIELD).performTextInput("retrofit")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("square/retrofit").fetchSemanticsNodes().isNotEmpty()
        }

        // A recreate() is the cheapest honest test of the SavedStateHandle and ViewModel work
        // from lesson 07: the query and the results both have to come back.
        composeRule.activityRule.scenario.recreate()

        openSearchTab()
        composeRule.onNodeWithText("square/retrofit").assertIsDisplayed()
    }

    @Test
    fun shorteningTheQueryBelowTheMinimumReturnsToThePrompt() {
        openSearchTab()
        val field = composeRule.onNodeWithTag(SearchTestTags.QUERY_FIELD)
        field.performTextInput("okhttp")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("square/okhttp").fetchSemanticsNodes().isNotEmpty()
        }

        field.performTextClearance()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule
                .onAllNodesWithText("Type at least two characters to search.")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    /** The labs share one Activity behind a TabRow, so a test has to get to the right tab. */
    private fun openSearchTab() {
        composeRule.onNodeWithText("07 · Search").performClick()
    }
}
