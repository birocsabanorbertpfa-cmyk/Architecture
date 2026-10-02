package hu.csabi.architecture.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.csabi.architecture.R
import hu.csabi.architecture.core.ui.UiText
import hu.csabi.architecture.core.ui.asString
import hu.csabi.architecture.core.ui.resolve
import hu.csabi.architecture.domain.model.Repo
import hu.csabi.architecture.domain.model.RepoId
import hu.csabi.architecture.domain.model.Stars
import hu.csabi.architecture.domain.model.Username
import hu.csabi.architecture.ui.theme.ArchitectureTheme

/**
 * Lesson 07 — route and screen are two different jobs.
 *
 * The **route** is the only part that knows about Hilt, lifecycles and effects. Keeping it
 * thin and separate is what lets [RepoSearchScreen] below be a pure function of its state:
 * previewable, and assertable in lesson 11 without a ViewModel or a graph.
 */
@Composable
fun RepoSearchRoute(modifier: Modifier = Modifier) {
    val viewModel: RepoSearchViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // Captured here, in composition, because the effect collector below is not composable.
    val context = LocalContext.current

    /**
     * Effects are collected in a `LaunchedEffect`, never read from state. `Unit` as the key
     * means this starts once per composition lifetime — re-keying it on changing state would
     * restart the collector and lose effects.
     */
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is RepoSearchEffect.ShowMessage ->
                    snackbarHostState.showSnackbar(effect.text.resolve(context))

                // A real app navigates here; the snackbar stands in until navigation exists.
                is RepoSearchEffect.OpenRepo ->
                    snackbarHostState.showSnackbar(effect.repo.fullName)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        RepoSearchScreen(
            state = state,
            onEvent = viewModel::onEvent,
            modifier = Modifier.padding(padding),
        )
    }
}

/**
 * Stateless by construction: it receives a state and a single event sink. There is no
 * ViewModel reference, no `remember` holding business state, and nothing to mock in a test.
 */
@Composable
fun RepoSearchScreen(
    state: RepoSearchUiState,
    onEvent: (RepoSearchEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { onEvent(RepoSearchEvent.QueryChanged(it)) },
            label = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // A thin bar rather than a full-screen spinner: the list below stays usable while a
        // refresh is in flight, which is only possible because state carries both at once.
        if (state.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        state.error?.let { error ->
            ErrorBanner(
                message = error,
                onRetry = { onEvent(RepoSearchEvent.Retry) },
                onDismiss = { onEvent(RepoSearchEvent.ErrorDismissed) },
            )
        }

        when {
            state.showPrompt -> Hint(stringResource(R.string.search_prompt))
            state.showEmpty -> Hint(stringResource(R.string.search_empty, state.query))
            else -> RepoList(
                repos = state.repos,
                onClick = { onEvent(RepoSearchEvent.RepoClicked(it)) },
            )
        }
    }
}

@Composable
private fun RepoList(repos: List<Repo>, onClick: (Repo) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // `key` keeps item identity stable across reorders, so Compose reuses the right
        // nodes instead of rebuilding the list on every result change.
        items(items = repos, key = { it.id.value }) { repo ->
            RepoRow(repo = repo, onClick = { onClick(repo) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun RepoRow(repo: Repo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(repo.fullName, style = MaterialTheme.typography.titleSmall)
            repo.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            repo.language?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
        }
        Text("★ ${repo.stars.formatted()}", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ErrorBanner(message: UiText, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(message.asString(), style = MaterialTheme.typography.bodyMedium)
            Row {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.search_retry)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.search_dismiss)) }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Text(text = text, modifier = Modifier.padding(24.dp))
    }
}

// ── Previews ────────────────────────────────────────────────────────────────────
// Every interesting state is previewable because the screen takes a plain data class. This
// is the practical reason to keep the composable stateless, not a stylistic preference.

private val sampleRepos = listOf(
    Repo(RepoId(1), "retrofit", Username("square"), "A type-safe HTTP client", Stars(43_000), "Java"),
    Repo(RepoId(2), "okhttp", Username("square"), "HTTP client for JVM and Android", Stars(46_200), "Kotlin"),
)

@Preview(name = "Content", showBackground = true)
@Composable
private fun PreviewContent() = ArchitectureTheme {
    RepoSearchScreen(RepoSearchUiState(query = "http", repos = sampleRepos), onEvent = {})
}

@Preview(name = "Refreshing with stale list", showBackground = true)
@Composable
private fun PreviewRefreshing() = ArchitectureTheme {
    RepoSearchScreen(
        RepoSearchUiState(query = "http", repos = sampleRepos, isLoading = true),
        onEvent = {},
    )
}

@Preview(name = "Error over content", showBackground = true)
@Composable
private fun PreviewErrorOverContent() = ArchitectureTheme {
    RepoSearchScreen(
        RepoSearchUiState(
            query = "http",
            repos = sampleRepos,
            error = UiText.Resource(R.string.error_network),
        ),
        onEvent = {},
    )
}

@Preview(name = "Empty", showBackground = true)
@Composable
private fun PreviewEmpty() = ArchitectureTheme {
    RepoSearchScreen(RepoSearchUiState(query = "zzzzz"), onEvent = {})
}

@Preview(name = "Prompt", showBackground = true)
@Composable
private fun PreviewPrompt() = ArchitectureTheme {
    RepoSearchScreen(RepoSearchUiState(), onEvent = {})
}
