package hu.csabi.architecture.feature.lab

import hu.csabi.architecture.core.coroutines.AppDispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.zip
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.system.measureTimeMillis

/**
 * Lesson 03 — runnable Flow demos.
 *
 * A Flow is a *cold* asynchronous stream: the builder block is a recipe, and it only runs
 * when someone collects. Everything else — operators, context rules, error handling —
 * follows from that single property.
 */
class FlowLab(
    private val dispatchers: AppDispatchers,
    private val log: suspend (String) -> Unit,
) {

    /** Emits 1..count with a delay between values. Nothing happens until it is collected. */
    private fun numbers(count: Int, delayMillis: Long = 100) = flow {
        log("  [producer] flow block starts")
        for (i in 1..count) {
            delay(delayMillis)
            emit(i)
        }
        log("  [producer] flow block ends")
    }

    // 1 ─────────────────────────────────────────────────────────────────────
    /**
     * Cold means every collector gets its own execution of the builder: two collectors do
     * not share work, and a flow nobody collects never runs at all.
     */
    suspend fun coldStream() {
        val source = numbers(2)
        log("Flow created — note that nothing has run yet")
        delay(300)

        log("First collect:")
        source.collect { log("  received $it") }
        log("Second collect — the block runs again from scratch:")
        source.collect { log("  received $it") }
    }

    // 2 ─────────────────────────────────────────────────────────────────────
    /**
     * `transform` is the general operator: it may emit zero, one or many values per input,
     * which is why `map` and `filter` are just special cases of it.
     */
    suspend fun operators() {
        numbers(5, delayMillis = 50)
            .transform { value ->
                if (value % 2 == 0) {
                    emit("even:$value")
                    emit("doubled:${value * 2}")
                }
            }
            .onEach { log("  $it") }
            .onCompletion { cause -> log("  onCompletion (cause=$cause)") }
            .drain()
    }

    // 3 ─────────────────────────────────────────────────────────────────────
    /**
     * Context preservation: a flow must emit from the coroutine that collects it, so
     * calling `withContext` inside a `flow {}` builder throws. `flowOn` is the sanctioned
     * way to move work, and it only affects the operators **upstream** of itself.
     */
    suspend fun contextAndFlowOn() {
        flow {
            log("  [upstream]  ${threadName()}")
            emit(1)
        }
            .map { value ->
                log("  [map]       ${threadName()}")
                value * 10
            }
            .flowOn(dispatchers.default) // applies to the two stages above, not below
            .collect { log("  [collector] ${threadName()} value=$it") }
    }

    // 4 ─────────────────────────────────────────────────────────────────────
    /**
     * Backpressure. By default a fast producer and a slow collector run sequentially, so
     * their costs add up. The three standard answers differ in what they give up:
     *
     * - `buffer`        keeps every value and lets the stages overlap
     * - `conflate`      drops intermediate values, always takes the newest available
     * - `collectLatest` cancels the in-flight collector body when a new value arrives
     */
    suspend fun backpressure() {
        val producer = numbers(5, delayMillis = 100)

        val plain = measureTimeMillis { producer.collect { delay(200) } }
        log("no operator:   $plain ms (100 + 200 per item)")

        val buffered = measureTimeMillis { producer.buffer().collect { delay(200) } }
        log("buffer():      $buffered ms — all 5 values processed, stages overlap")

        var conflated = 0
        val conflatedTime = measureTimeMillis {
            producer.conflate().collect { conflated++; delay(200) }
        }
        log("conflate():    $conflatedTime ms — only $conflated values survived")

        var completed = 0
        val latestTime = measureTimeMillis {
            producer.collectLatest { delay(200); completed++ }
        }
        log("collectLatest: $latestTime ms — only $completed collector body finished")
    }

    // 5 ─────────────────────────────────────────────────────────────────────
    /**
     * `combine` re-emits whenever *either* source emits, pairing the latest value of each —
     * the right tool for "query + filter -> results". `zip` waits for a value from both and
     * pairs them strictly, completing when the shorter source ends.
     */
    suspend fun combineVsZip() {
        val fast = numbers(4, delayMillis = 60).map { "f$it" }
        val slow = numbers(2, delayMillis = 180).map { "s$it" }

        log("combine:")
        fast.combine(slow) { a, b -> "$a+$b" }.collect { log("  $it") }

        log("zip:")
        fast.zip(slow) { a, b -> "$a+$b" }.collect { log("  $it") }
    }

    // 6 ─────────────────────────────────────────────────────────────────────
    /**
     * `catch` only sees exceptions coming from **upstream**; a failure thrown inside the
     * collector is deliberately not swallowed. `retryWhen` exposes the attempt index, so
     * exponential backoff is a two-line affair.
     */
    suspend fun errorsAndRetry() {
        var attempt = 0
        flow {
            attempt++
            log("  attempt #$attempt")
            if (attempt < 3) throw IOException("connection reset")
            emit("payload")
        }
            .retryWhen { cause, attemptIndex ->
                val shouldRetry = cause is IOException && attemptIndex < 3
                if (shouldRetry) {
                    val backoff = 100L * (attemptIndex + 1)
                    log("  retrying in $backoff ms")
                    delay(backoff)
                }
                shouldRetry
            }
            .catch { cause -> log("  catch: ${cause.message}") }
            .collect { log("  collected $it") }
    }

    // 7 ─────────────────────────────────────────────────────────────────────
    /**
     * Cold to hot: `shareIn` runs one upstream execution and fans it out to every
     * subscriber. `SharingStarted.WhileSubscribed` stops that upstream once the last
     * subscriber leaves — the setting that keeps a screen from polling in the background.
     */
    suspend fun coldToHot() = coroutineScope {
        val shared = numbers(4, delayMillis = 150)
            .shareIn(this, SharingStarted.WhileSubscribed(), replay = 0)

        val a = launch { shared.collect { log("  subscriber A: $it") } }
        delay(250)
        val b = launch { shared.collect { log("  subscriber B: $it (joined late, no replay)") } }

        delay(600)
        a.cancel()
        b.cancel()
        log("Both subscribers gone — WhileSubscribed stops the upstream")
    }

    private fun threadName(): String = Thread.currentThread().name
}

/** `collect()` with no action simply drains the flow, running its side effects. */
private suspend fun <T> Flow<T>.drain() = collect { }
