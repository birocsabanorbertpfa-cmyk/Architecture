package hu.csabi.architecture.feature.lab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import hu.csabi.architecture.core.model.Repo

@Composable
fun FlowLabScreen(
    modifier: Modifier = Modifier,
    viewModel: FlowLabViewModel = viewModel(),
) {
    // collectAsStateWithLifecycle stops collecting when the screen goes to the background.
    // Plain collectAsState() would keep the flow hot behind a locked screen.
    val query by viewModel.query.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val log by viewModel.log.collectAsStateWithLifecycle()
    val runningDemo by viewModel.runningDemo.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // One-off events are consumed in a LaunchedEffect, not read as state.
    LaunchedEffect(Unit) {
        viewModel.events.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Search as you type", style = MaterialTheme.typography.titleMedium)
        Text(
            "debounce 300 ms → distinctUntilChanged → flatMapLatest → stateIn",
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            label = { Text("Repository name or language") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        SearchResult(state = searchState, modifier = Modifier.height(180.dp))

        Text("Flow operators", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (demo in FlowLabViewModel.Demo.entries) {
                FilterChip(
                    selected = runningDemo == demo.title,
                    onClick = { viewModel.run(demo) },
                    enabled = runningDemo == null,
                    label = { Text(demo.title) },
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(modifier = Modifier.padding(8.dp)) {
                items(log) { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        SnackbarHost(snackbarHostState) { data -> Snackbar(snackbarData = data) }
    }
}

@Composable
private fun SearchResult(state: SearchUiState, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        // Every state is rendered explicitly — the compiler enforces that none is forgotten.
        when (state) {
            SearchUiState.Idle -> Hint("Type at least two characters")
            SearchUiState.Loading -> Column(Modifier.padding(12.dp)) { CircularProgressIndicator() }
            SearchUiState.Empty -> Hint("No repository matched")
            is SearchUiState.Error -> Hint(state.message)
            is SearchUiState.Content -> LazyColumn(modifier = Modifier.padding(8.dp)) {
                items(state.repos) { repo -> RepoRow(repo) }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text = text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun RepoRow(repo: Repo) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text("${repo.fullName}  ★ ${repo.stars.formatted()}", style = MaterialTheme.typography.bodyMedium)
        repo.description?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
    }
}
