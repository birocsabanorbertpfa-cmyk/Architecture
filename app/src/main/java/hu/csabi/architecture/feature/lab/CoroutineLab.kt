package hu.csabi.architecture.feature.lab

import hu.csabi.architecture.core.coroutines.AppDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.coroutineContext
import kotlin.system.measureTimeMillis

/**
 * Lesson 02 — futtatható coroutine demók.
 *
 * Minden demó `suspend fun`: nem indít saját coroutine-t, nem ismer scope-ot.
 * Ez a helyes API-forma — a hívó dönti el, hol és meddig fut (structured concurrency).
 */
class CoroutineLab(
    private val dispatchers: AppDispatchers,
    private val log: suspend (String) -> Unit,
) {

    /** Szimulált hálózati hívás: felfüggeszt, nem blokkol szálat. */
    private suspend fun fetch(name: String, millis: Long): String {
        log("  $name indul   [${threadName()}]")
        delay(millis)
        log("  $name kész")
        return name
    }

    // 1 ─────────────────────────────────────────────────────────────────────
    /**
     * `delay` felfüggeszti a coroutine-t, de a szálat visszaadja a poolnak.
     * Ezért fut le két 600 ms-os hívás párhuzamosan ~600 ms alatt egyetlen szálon is.
     */
    suspend fun sequentialVsParallel() {
        val sequential = measureTimeMillis {
            fetch("A", 600)
            fetch("B", 600)
        }
        log("Szekvenciális: $sequential ms")

        val parallel = measureTimeMillis {
            coroutineScope {
                val a = async { fetch("A", 600) }
                val b = async { fetch("B", 600) }
                awaitAll(a, b)
            }
        }
        log("Párhuzamos (async): $parallel ms")
    }

    // 2 ─────────────────────────────────────────────────────────────────────
    /**
     * A lemondás **kooperatív**: a coroutine attól nem áll le, hogy cancel()-t hívsz,
     * csak akkor, ha felfüggesztési ponthoz ér (delay, withContext...) vagy maga
     * ellenőrzi (`ensureActive()` / `isActive` / `yield()`).
     */
    suspend fun cooperativeCancellation() {
        log("Kooperatív ciklus (ensureActive) — ez leáll cancel-re")
        withContext(dispatchers.default) {
            var i = 0
            while (i < 1_000_000_000) {
                if (i % 50_000_000 == 0) {
                    coroutineContext.ensureActive() // itt dob CancellationException-t
                    log("  iteráció $i [${threadName()}]")
                }
                i++
            }
        }
    }

    /**
     * Cleanup lemondáskor: a `finally` lefut, de benne **felfüggeszteni már nem lehet**,
     * mert a coroutine context már cancelled. Aki mégis muszáj (pl. cache zárás),
     * az `withContext(NonCancellable)`-be teszi.
     */
    suspend fun cancellationCleanup() {
        try {
            log("Erőforrás megnyitva")
            delay(10_000)
        } finally {
            withContext(NonCancellable) {
                delay(50)
                log("Erőforrás lezárva (NonCancellable blokkban)")
            }
        }
    }

    // 3 ─────────────────────────────────────────────────────────────────────
    /**
     * `coroutineScope`: ha EGY gyerek hibázik, a scope lemondja az összes testvért,
     * és a kivétel kibuborékol. "Minden vagy semmi" — ezt akarod, ha az eredmények
     * együtt értelmesek (pl. user + repo lista egy képernyőhöz).
     */
    suspend fun allOrNothing() {
        try {
            coroutineScope {
                async { fetch("gyors", 200) }
                async { fetch("lassú", 2_000) }
                async<Unit> {
                    delay(400)
                    log("  hibás ág dob")
                    error("szándékos hiba")
                }
            }
        } catch (e: IllegalStateException) {
            log("coroutineScope: elkapva '${e.message}' — a 'lassú' ág is megszakadt")
        }
    }

    /**
     * `supervisorScope`: a gyerekek hibája NEM terjed felfelé és oldalra.
     * Ezt akkor használd, ha a részeredmények önállóan is értékesek.
     */
    suspend fun independentChildren() {
        supervisorScope {
            val ok = async { fetch("ok-ág", 300) }
            val bad = async<String> {
                delay(100)
                error("független hiba")
            }
            // Fontos: az await() itt DOBJA a hibát, ezért ágat kell kezelni.
            runCatching { bad.await() }.onFailure { log("  hibás ág elbukott: ${it.message}") }
            log("supervisorScope: a másik ág túlélte -> ${ok.await()}")
        }
    }

    // 4 ─────────────────────────────────────────────────────────────────────
    /** `withTimeout` a határidő lejártakor lemondja a blokkot — TimeoutCancellationException. */
    suspend fun timeout() {
        try {
            withTimeout(500) { fetch("lassú hívás", 3_000) }
        } catch (e: CancellationException) {
            log("withTimeout: ${e::class.simpleName} — a hívás lemondva")
        }
    }

    // 5 ─────────────────────────────────────────────────────────────────────
    /**
     * `withContext` átvált dispatchert, és a blokk végén visszavált.
     * A suspend függvény felelőssége, hogy "main-safe" legyen — a hívónak nem
     * kell tudnia, milyen szálon fut a munka.
     */
    suspend fun dispatchers() {
        log("hívó szál:  [${threadName()}]")
        withContext(dispatchers.io) { log("Dispatchers.IO:      [${threadName()}]") }
        withContext(dispatchers.default) { log("Dispatchers.Default: [${threadName()}]") }
        withContext(dispatchers.main) { log("Dispatchers.Main:    [${threadName()}]") }
    }

    private fun threadName(): String = Thread.currentThread().name
}
