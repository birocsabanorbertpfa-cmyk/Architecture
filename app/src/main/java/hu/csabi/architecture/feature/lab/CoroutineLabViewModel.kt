package hu.csabi.architecture.feature.lab

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.csabi.architecture.core.coroutines.AppDispatchers
import hu.csabi.architecture.core.coroutines.DefaultAppDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lesson 02 — a `viewModelScope` a structured concurrency belépési pontja Androidon.
 *
 * Egy `SupervisorJob` + `Dispatchers.Main.immediate`, amit az `onCleared()`
 * automatikusan lemond. Ezért nem szivárog a munka, ha a user elnavigál.
 *
 * (Az állapotkezelés itt szándékosan primitív — StateFlow a 03., rendes UiState a 07. leckében.)
 */
class CoroutineLabViewModel(
    private val dispatchers: AppDispatchers = DefaultAppDispatchers,
) : ViewModel() {

    val logLines = mutableStateListOf<String>()

    var runningDemo by mutableStateOf<String?>(null)
        private set

    private var currentJob: Job? = null
    private var startedAt = 0L

    private val lab = CoroutineLab(dispatchers) { line -> appendLine(line) }

    /**
     * A log írása Compose state-be történik, ami CSAK a main szálról biztonságos.
     * A demók viszont IO/Default dispatcheren is futnak — ezért váltunk vissza.
     */
    private suspend fun appendLine(line: String) = withContext(dispatchers.main) {
        val elapsed = System.currentTimeMillis() - startedAt
        logLines += "%4d ms | %s".format(elapsed, line)
    }

    fun run(demo: Demo) {
        currentJob?.cancel()
        logLines.clear()
        startedAt = System.currentTimeMillis()
        runningDemo = demo.title

        // A launch a viewModelScope GYEREKE lesz: a scope lemondása ezt is lemondja.
        currentJob = viewModelScope.launch {
            try {
                when (demo) {
                    Demo.SequentialVsParallel -> lab.sequentialVsParallel()
                    Demo.Cancellation -> lab.cooperativeCancellation()
                    Demo.Cleanup -> lab.cancellationCleanup()
                    Demo.AllOrNothing -> lab.allOrNothing()
                    Demo.Independent -> lab.independentChildren()
                    Demo.Timeout -> lab.timeout()
                    Demo.Dispatchers -> lab.dispatchers()
                }
                appendLine("── kész ──")
            } catch (cancellation: CancellationException) {
                // A CancellationException normális vezérlési folyam, nem hiba.
                // Lemondott contextben már nem lehet felfüggeszteni -> NonCancellable kell.
                withContext(NonCancellable) { appendLine("── megszakítva ──") }
                throw cancellation
            } catch (throwable: Throwable) {
                appendLine("── hiba: ${throwable.message} ──")
            } finally {
                runningDemo = null
            }
        }
    }

    /** `cancel()` csak JELZI a lemondást; a coroutine a következő ellenőrzési ponton áll le. */
    fun cancel() {
        currentJob?.cancel(CancellationException("felhasználói megszakítás"))
    }

    enum class Demo(val title: String) {
        SequentialVsParallel("1. Szekvenciális vs. async"),
        Cancellation("2. Kooperatív lemondás"),
        Cleanup("3. Cleanup + NonCancellable"),
        AllOrNothing("4. coroutineScope: mindent visz"),
        Independent("5. supervisorScope: független ágak"),
        Timeout("6. withTimeout"),
        Dispatchers("7. Dispatcher váltás"),
    }
}
