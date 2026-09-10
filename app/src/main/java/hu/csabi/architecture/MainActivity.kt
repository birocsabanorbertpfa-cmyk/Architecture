package hu.csabi.architecture

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import hu.csabi.architecture.core.model.SearchQuery
import hu.csabi.architecture.core.model.Stars
import hu.csabi.architecture.core.model.Username
import hu.csabi.architecture.core.model.searchQuery
import hu.csabi.architecture.core.result.AppResult
import hu.csabi.architecture.core.result.appRunCatching
import hu.csabi.architecture.core.result.getOrNull
import hu.csabi.architecture.core.result.map
import hu.csabi.architecture.ui.theme.ArchitectureTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ArchitectureTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    WelcomeScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

private val demoQuery: SearchQuery = searchQuery {
    term("compose")
    language("Kotlin")
    minStars(Stars(1_000))
    user(Username("android"))
}

private val demoResult: AppResult<String> = appRunCatching { Stars(12_345) }
    .map { "★ ${it.formatted()}" }

@Composable
fun WelcomeScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "Architecture Playground", style = MaterialTheme.typography.headlineMedium)
        Text(text = "Lesson 01 — Kotlin mélyvíz", style = MaterialTheme.typography.titleSmall)
        Text(text = "DSL query: ${demoQuery.raw}", style = MaterialTheme.typography.bodyMedium)
        Text(
            text = "AppResult.map: ${demoResult.getOrNull()}",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WelcomeScreenPreview() {
    ArchitectureTheme { WelcomeScreen() }
}
