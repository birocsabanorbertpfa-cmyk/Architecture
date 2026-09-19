package hu.csabi.architecture.core.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Lesson 02 — never hardcode a dispatcher.
 *
 * Calling `Dispatchers.IO` directly inside production code hurts in tests: it cannot be
 * swapped for a deterministic test dispatcher. Hiding it behind an interface fixes that —
 * lesson 06 injects this with Hilt, lesson 10 replaces it with a test double.
 *
 * - Main: UI only. State updates, never blocking work.
 * - IO: blocking calls (network, disk, database). Large elastic pool, since most of those
 *   threads are parked waiting on I/O anyway.
 * - Default: CPU-bound work. Pool size equals the core count, because more would not help.
 */
interface AppDispatchers {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
}

object DefaultAppDispatchers : AppDispatchers {
    override val main: CoroutineDispatcher = Dispatchers.Main.immediate
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
}
