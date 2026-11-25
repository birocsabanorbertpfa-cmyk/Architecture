package hu.csabi.architecture.feature.async

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AsyncLabScreen(
    modifier: Modifier = Modifier,
    viewModel: AsyncLabViewModel = hiltViewModel(),
) {
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val log by viewModel.log.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Lesson 13 — advanced async", style = MaterialTheme.typography.titleMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(if (isOnline) "online" else "offline") },
            )
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(if (isSyncing) "sync pending" else "sync idle") },
            )
        }

        Text(
            "The online chip comes from a callbackFlow over ConnectivityManager. " +
                "Toggle airplane mode to watch it change.",
            style = MaterialTheme.typography.bodySmall,
        )

        Button(onClick = viewModel::runBackoffDemo, modifier = Modifier.fillMaxWidth()) {
            Text("retryWithBackoff")
        }
        Button(onClick = viewModel::runRestartDemo, modifier = Modifier.fillMaxWidth()) {
            Text("restartWhen(isOnline)")
        }
        Button(onClick = viewModel::onTap, modifier = Modifier.fillMaxWidth()) {
            Text("throttleFirst — tap me repeatedly")
        }
        Button(onClick = viewModel::enqueueSync, modifier = Modifier.fillMaxWidth()) {
            Text("Enqueue WorkManager sync")
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
    }
}
