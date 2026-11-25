package hu.csabi.architecture.core.coroutines

import kotlin.math.pow
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch

/**
 * Lesson 13 — writing operators instead of repeating pipelines.
 *
 * An operator is just a function from `Flow<T>` to `Flow<R>`: there is no registry to join
 * and nothing to subclass. The reason to write one is the reason to write any function —
 * the backoff logic below was about to appear in two places, and the second copy is where
 * the bug would have lived.
 */

/**
 * Retry with **exponential backoff and jitter**.
 *
 * Three decisions worth naming:
 *
 *  - `delay * 2^attempt` rather than a fixed delay. A server that is down stays down for
 *    longer than 200 ms, and a client retrying every 200 ms is a client making the outage
 *    worse.
 *  - **jitter**, the part that usually gets left out. Without it every device that failed at
 *    the same moment retries at the same moment — the thundering herd that keeps a
 *    recovering server down. A random fraction spreads the load.
 *  - a `shouldRetry` predicate. Repeating a 404 or a validation error is pure waste; only
 *    transient failures are worth retrying, which is what `AppError.isRetryable` encodes.
 */
fun <T> Flow<T>.retryWithBackoff(
    maxAttempts: Int = 3,
    initialDelayMillis: Long = 500,
    maxDelayMillis: Long = 10_000,
    jitterFactor: Double = 0.2,
    shouldRetry: (Throwable) -> Boolean = { true },
): Flow<T> = retryWhen { cause, attempt ->
    if (attempt >= maxAttempts || !shouldRetry(cause)) return@retryWhen false

    val exponential = initialDelayMillis * 2.0.pow(attempt.toDouble())
    val capped = exponential.coerceAtMost(maxDelayMillis.toDouble())
    val jitter = capped * jitterFactor * Random.nextDouble(-1.0, 1.0)

    delay((capped + jitter).toLong().coerceAtLeast(0))
    true
}

/**
 * Emit the first value, then ignore everything for [windowMillis].
 *
 * The opposite trade-off from `debounce`, and they are not interchangeable:
 *  - `debounce` waits for quiet, so the **last** value of a burst wins — right for a search
 *    field, where only the final query matters.
 *  - `throttleFirst` reacts immediately and drops the rest — right for a button, where the
 *    first tap should act and the accidental second one should not.
 *
 * Using `debounce` on a button is the classic mistake: every tap feels sluggish.
 *
 * Implemented with `channelFlow` and a `delay`-based window rather than by reading the wall
 * clock, which is what makes it testable on a virtual clock. A `Flow` cannot emit from a
 * different coroutine than the one collecting it, and `channelFlow` is the sanctioned way
 * around that: it gives a `SendChannel` that *is* safe to use concurrently.
 */
fun <T> Flow<T>.throttleFirst(windowMillis: Long): Flow<T> = channelFlow {
    var window: Job? = null
    collect { value ->
        if (window?.isActive != true) {
            send(value)
            window = launch { delay(windowMillis) }
        }
    }
}

/**
 * Restart the upstream every time [trigger] turns true, and stop it when it turns false.
 *
 * This is how "reload when the connection comes back" is written without a single
 * `if (isOnline)`: the trigger is a flow, and `flatMapLatest` cancels the previous attempt
 * and starts a fresh one.
 *
 * `distinctUntilChanged` on the trigger is not cosmetic — Android's connectivity callbacks
 * fire repeatedly for the same state, and without it the upstream restarts on every one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Flow<T>.restartWhen(trigger: Flow<Boolean>): Flow<T> =
    trigger.distinctUntilChanged().flatMapLatest { active ->
        if (active) this else emptyFlow()
    }

/** Prepend a value, so a cold flow has something to show before its first real emission. */
fun <T> Flow<T>.startWith(initial: T): Flow<T> = onStart { emit(initial) }

/**
 * Keep only the newest value when the collector cannot keep up, and never suspend the
 * producer.
 *
 * `conflate()` does the same thing, but spelling it out with `buffer` shows what conflation
 * actually is: a one-slot buffer whose overflow policy drops the oldest entry. Useful when
 * the values are *states* (the latest is the only one that matters) and wrong when they are
 * *events* (where dropping one loses information).
 */
fun <T> Flow<T>.latestOnly(): Flow<T> =
    buffer(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
