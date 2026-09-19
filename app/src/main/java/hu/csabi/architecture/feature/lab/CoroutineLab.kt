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
 * Lesson 02 — runnable coroutine demos.
 *
 * Every demo is a plain `suspend fun`: it never launches its own coroutine and never holds
 * a scope. That is the correct shape for a suspending API — the caller decides where the
 * work runs and how long it lives (structured concurrency).
 */
class CoroutineLab(
    private val dispatchers: AppDispatchers,
    private val log: suspend (String) -> Unit,
) {

    /** Fake network call: suspends without blocking a thread. */
    private suspend fun fetch(name: String, millis: Long): String {
        log("  $name started [${threadName()}]")
        delay(millis)
        log("  $name done")
        return name
    }

    // 1 ─────────────────────────────────────────────────────────────────────
    /**
     * `delay` suspends the coroutine but hands the thread back to the pool, which is why
     * two 600 ms calls finish in ~600 ms in parallel — even on a single thread.
     */
    suspend fun sequentialVsParallel() {
        val sequential = measureTimeMillis {
            fetch("A", 600)
            fetch("B", 600)
        }
        log("Sequential: $sequential ms")

        val parallel = measureTimeMillis {
            coroutineScope {
                val a = async { fetch("A", 600) }
                val b = async { fetch("B", 600) }
                awaitAll(a, b)
            }
        }
        log("Parallel (async): $parallel ms")
    }

    // 2 ─────────────────────────────────────────────────────────────────────
    /**
     * Cancellation is **cooperative**: calling `cancel()` does not stop anything by itself.
     * A coroutine stops when it hits a suspension point (delay, withContext, ...) or checks
     * for itself via `ensureActive()` / `isActive` / `yield()`.
     */
    suspend fun cooperativeCancellation() {
        log("Cooperative loop (ensureActive) — this one reacts to cancel")
        withContext(dispatchers.default) {
            var i = 0
            while (i < 1_000_000_000) {
                if (i % 50_000_000 == 0) {
                    coroutineContext.ensureActive() // throws CancellationException here
                    log("  iteration $i [${threadName()}]")
                }
                i++
            }
        }
    }

    /**
     * Cleanup on cancellation: `finally` does run, but you **cannot suspend inside it**
     * because the context is already cancelled. Work that must still happen (closing a
     * cache, flushing analytics) goes into `withContext(NonCancellable)`.
     */
    suspend fun cancellationCleanup() {
        try {
            log("Resource opened")
            delay(10_000)
        } finally {
            withContext(NonCancellable) {
                delay(50)
                log("Resource closed (inside NonCancellable)")
            }
        }
    }

    // 3 ─────────────────────────────────────────────────────────────────────
    /**
     * `coroutineScope`: if ONE child fails, the scope cancels its siblings and rethrows.
     * All-or-nothing — what you want when the results only make sense together (e.g. user
     * profile + repo list for the same screen).
     */
    suspend fun allOrNothing() {
        try {
            coroutineScope {
                async { fetch("fast", 200) }
                async { fetch("slow", 2_000) }
                async<Unit> {
                    delay(400)
                    log("  failing branch throws")
                    error("deliberate failure")
                }
            }
        } catch (e: IllegalStateException) {
            log("coroutineScope: caught '${e.message}' — the 'slow' branch was cancelled too")
        }
    }

    /**
     * `supervisorScope`: a child failure does not propagate up or sideways. Use it when
     * partial results are still valuable on their own.
     */
    suspend fun independentChildren() {
        supervisorScope {
            val ok = async { fetch("surviving branch", 300) }
            val bad = async<String> {
                delay(100)
                error("independent failure")
            }
            // Note: await() still throws here, so the failing branch needs its own handling.
            runCatching { bad.await() }.onFailure { log("  failing branch lost: ${it.message}") }
            log("supervisorScope: the other branch survived -> ${ok.await()}")
        }
    }

    // 4 ─────────────────────────────────────────────────────────────────────
    /** `withTimeout` cancels the block when the deadline passes — TimeoutCancellationException. */
    suspend fun timeout() {
        try {
            withTimeout(500) { fetch("slow call", 3_000) }
        } catch (e: CancellationException) {
            log("withTimeout: ${e::class.simpleName} — the call was cancelled")
        }
    }

    // 5 ─────────────────────────────────────────────────────────────────────
    /**
     * `withContext` switches dispatcher for the block and switches back at the end. Being
     * main-safe is the suspending function's responsibility, not the caller's.
     */
    suspend fun dispatchers() {
        log("caller thread:       [${threadName()}]")
        withContext(dispatchers.io) { log("Dispatchers.IO:      [${threadName()}]") }
        withContext(dispatchers.default) { log("Dispatchers.Default: [${threadName()}]") }
        withContext(dispatchers.main) { log("Dispatchers.Main:    [${threadName()}]") }
    }

    private fun threadName(): String = Thread.currentThread().name
}
