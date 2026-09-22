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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import hu.csabi.architecture.feature.lab.CoroutineLabScreen
import hu.csabi.architecture.feature.lab.FlowLabScreen
import hu.csabi.architecture.feature.network.NetworkLabScreen
import hu.csabi.architecture.ui.theme.ArchitectureTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ArchitectureTheme { LabHost() }
        }
    }
}

private enum class LabTab(val title: String) {
    Coroutines("02 · Coroutines"),
    Flow("03 · Flow"),
    Network("04 · Network"),
}

@Composable
private fun LabHost() {
    // rememberSaveable so the selected tab survives configuration changes.
    var selected by rememberSaveable { mutableStateOf(LabTab.Network) }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            TabRow(selectedTabIndex = selected.ordinal) {
                for (tab in LabTab.entries) {
                    Tab(
                        selected = tab == selected,
                        onClick = { selected = tab },
                        text = { Text(tab.title, style = MaterialTheme.typography.labelLarge) },
                    )
                }
            }
            when (selected) {
                LabTab.Coroutines -> CoroutineLabScreen()
                LabTab.Flow -> FlowLabScreen()
                LabTab.Network -> NetworkLabScreen()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LabHostPreview() {
    ArchitectureTheme { LabHost() }
}
