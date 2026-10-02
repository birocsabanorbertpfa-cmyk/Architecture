package hu.csabi.architecture.feature.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.csabi.architecture.domain.model.Repo

@Composable
fun NetworkLabScreen(
    modifier: Modifier = Modifier,
    viewModel: NetworkLabViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Error mapping bench", style = MaterialTheme.typography.titleMedium)
        Text(
            "Each chip triggers a specific failure and shows the AppError it maps to.",
            style = MaterialTheme.typography.bodySmall,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (scenario in NetworkLabViewModel.Scenario.entries) {
                AssistChip(
                    onClick = { viewModel.trigger(scenario) },
                    label = { Text(scenario.title) },
                )
            }
        }

        StateCard(state = state, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StateCard(state: NetworkUiState, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        when (state) {
            NetworkUiState.Idle -> Hint("Pick a scenario above")
            NetworkUiState.Loading -> Column(Modifier.padding(12.dp)) { CircularProgressIndicator() }
            NetworkUiState.Empty -> Hint("No repository matched")
            is NetworkUiState.Failed -> Hint(
                if (state.retryable) "${state.message}  ·  retry may help" else state.message,
            )

            is NetworkUiState.Content -> LazyColumn(modifier = Modifier.padding(8.dp)) {
                items(state.repos) { repo -> RepoRow(repo) }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(12.dp),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun RepoRow(repo: Repo) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            "${repo.fullName}  ★ ${repo.stars.formatted()}",
            style = MaterialTheme.typography.bodyMedium,
        )
        repo.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
