package hu.csabi.architecture.feature.lab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun CoroutineLabScreen(
    modifier: Modifier = Modifier,
    viewModel: CoroutineLabViewModel = viewModel(),
) {
    val listState = rememberLazyListState()

    // Scroll as new lines arrive. LaunchedEffect ties the coroutine to the composable's
    // lifetime: when the screen leaves composition, the scope is cancelled.
    LaunchedEffect(viewModel.logLines.size) {
        if (viewModel.logLines.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.logLines.lastIndex)
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Lesson 02 — Coroutines", style = MaterialTheme.typography.titleMedium)

        for (demo in CoroutineLabViewModel.Demo.entries) {
            Button(
                onClick = { viewModel.run(demo) },
                enabled = viewModel.runningDemo == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(demo.title)
            }
        }

        OutlinedButton(
            onClick = viewModel::cancel,
            enabled = viewModel.runningDemo != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("cancel()")
        }

        Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(state = listState, modifier = Modifier.padding(8.dp)) {
                items(viewModel.logLines) { line ->
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
