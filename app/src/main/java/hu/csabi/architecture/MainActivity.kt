package hu.csabi.architecture

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import hu.csabi.architecture.feature.labs.LabScreenEntry
import hu.csabi.architecture.feature.labs.generatedLabScreens
import hu.csabi.architecture.feature.search.RepoSearchRoute
import hu.csabi.architecture.ui.theme.ArchitectureTheme

/**
 * Lesson 06 — `@AndroidEntryPoint` creates the `ActivityComponent` and makes
 * `hiltViewModel()` inside this Activity's composition able to reach the graph. Without it,
 * every `hiltViewModel()` call below would fail at runtime.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ArchitectureTheme { LabHost() }
        }
    }
}

/**
 * Lesson 14 — the tab list is generated, not maintained.
 *
 * This used to be an enum with one entry per lab plus a `when` that mapped each entry to a
 * composable: two places to edit for every new screen, and nothing to stop you editing only
 * one. Now each lab carries `@LabScreen(title, order)` and the KSP processor emits
 * [generatedLabScreens] at compile time.
 *
 * What that buys, in order of importance:
 *  - a new lab cannot be forgotten, because there is nothing to remember
 *  - the list is built during compilation, so there is no startup reflection and no R8 keep
 *    rule
 *  - a lab whose composable is not callable without arguments fails the **build**, with the
 *    processor pointing at the function
 *
 * The search screen is added by hand on purpose: it lives in `:feature:search`, and the
 * processor only sees the module it runs in. Generated registries are per module — worth
 * knowing before reaching for one.
 */
@Composable
private fun LabHost() {
    val tabs = remember {
        generatedLabScreens + LabScreenEntry(title = "07 · Search", order = 7) { RepoSearchRoute() }
    }
    val ordered = remember(tabs) { tabs.sortedBy { it.order } }

    // rememberSaveable so the selected tab survives configuration changes.
    var selectedTitle by rememberSaveable { mutableStateOf(ordered.last().title) }
    val selectedIndex = ordered.indexOfFirst { it.title == selectedTitle }.coerceAtLeast(0)

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            // Scrollable, because the number of tabs is now whatever the processor found.
            ScrollableTabRow(selectedTabIndex = selectedIndex) {
                ordered.forEachIndexed { index, tab ->
                    Tab(
                        selected = index == selectedIndex,
                        onClick = { selectedTitle = tab.title },
                        text = { Text(tab.title, style = MaterialTheme.typography.labelLarge) },
                    )
                }
            }
            ordered[selectedIndex].content()
        }
    }
}
