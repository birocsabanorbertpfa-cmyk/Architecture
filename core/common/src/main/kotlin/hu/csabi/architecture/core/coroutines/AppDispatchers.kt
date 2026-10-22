package hu.csabi.architecture.core.coroutines

import javax.inject.Inject
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Lesson 02 — never hardcode a dispatcher.
 *
 * Calling `Dispatchers.IO` directly inside production code hurts in tests: it cannot be
 * swapped for a deterministic test dispatcher. Hiding it behind an interface fixes that —
 * lesson 06 wires it with Hilt, lesson 10 replaces it with a test double.
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

/**
 * Lesson 06 — the qualifiers live next to the abstraction they describe, not in the DI
 * package, so `core` does not have to depend on the composition root.
 *
 * Dagger identifies a binding by its type, and three `CoroutineDispatcher` bindings would
 * collide. A qualifier makes the key `(type, qualifier)` instead, which is what lets all
 * three coexist. `BINARY` retention is enough: nothing reads these at runtime.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

/**
 * Constructor injection, so Dagger needs no module to build this: `@Inject` on the
 * constructor *is* the binding. Modules are only required for interfaces and for types you
 * do not own.
 *
 * The explicit `@param:` use-site target matters: Dagger reads the qualifier from the
 * constructor *parameter*, and Kotlin is changing where a bare annotation lands on a
 * `val` parameter. Being explicit keeps it unambiguous in both compiler versions.
 */
class DefaultAppDispatchers @Inject constructor(
    @param:MainDispatcher override val main: CoroutineDispatcher,
    @param:IoDispatcher override val io: CoroutineDispatcher,
    @param:DefaultDispatcher override val default: CoroutineDispatcher,
) : AppDispatchers
