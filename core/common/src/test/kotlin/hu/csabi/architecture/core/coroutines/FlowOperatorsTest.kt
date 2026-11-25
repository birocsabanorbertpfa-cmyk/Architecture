package hu.csabi.architecture.core.coroutines

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Lesson 13 — custom operators, tested on a virtual clock.
 *
 * Every operator here involves time, and none of these tests take any: `runTest` fast-forwards
 * `delay`. That is also why `throttleFirst` is implemented with `delay` rather than by reading
 * `System.currentTimeMillis` — a wall-clock implementation would force the test to sleep, and
 * a sleeping test is a flaky test on a loaded machine.
 */
class FlowOperatorsTest {

    @Test
    fun `retryWithBackoff retries until the flow succeeds`() = runTest {
        var attempts = 0
        val flow = flow {
            attempts++
            if (attempts < 3) throw IOException("boom")
            emit("ok")
        }

        val result = flow.retryWithBackoff(maxAttempts = 5, initialDelayMillis = 100).toList()

        assertThat(result).containsExactly("ok")
        assertThat(attempts).isEqualTo(3)
    }

    @Test
    fun `retryWithBackoff gives up after maxAttempts`() = runTest {
        var attempts = 0
        val flow = flow<String> {
            attempts++
            throw IOException("always down")
        }

        val error = runCatching {
            flow.retryWithBackoff(maxAttempts = 2, initialDelayMillis = 10).toList()
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IOException::class.java)
        // The original attempt plus two retries.
        assertThat(attempts).isEqualTo(3)
    }

    @Test
    fun `retryWithBackoff does not retry what the predicate rejects`() = runTest {
        var attempts = 0
        val flow = flow<String> {
            attempts++
            throw IllegalArgumentException("bad request")
        }

        runCatching {
            flow.retryWithBackoff(shouldRetry = { it is IOException }).toList()
        }

        // Retrying a validation error is pure waste, so it must not happen even once.
        assertThat(attempts).isEqualTo(1)
    }

    @Test
    fun `retryWithBackoff waits longer after each failure`() = runTest {
        var attempts = 0
        val flow = flow {
            attempts++
            if (attempts < 4) throw IOException("boom") else emit("ok")
        }

        val startedAt = testScheduler.currentTime
        flow.retryWithBackoff(maxAttempts = 5, initialDelayMillis = 1_000, jitterFactor = 0.0)
            .toList()
        val elapsed = testScheduler.currentTime - startedAt

        // 1000 + 2000 + 4000 with jitter switched off, so the growth is exact.
        // `testScheduler.currentTime` is the virtual clock: no real time passed at all.
        assertThat(elapsed).isEqualTo(7_000)
    }

    @Test
    fun `throttleFirst emits the first value and drops the rest of the window`() = runTest {
        val source = MutableSharedFlow<Int>(extraBufferCapacity = 16)

        source.throttleFirst(windowMillis = 1_000).test {
            source.tryEmit(1)
            assertThat(awaitItem()).isEqualTo(1)

            // Same window: dropped, not queued.
            source.tryEmit(2)
            source.tryEmit(3)
            advanceTimeBy(500)
            runCurrent()
            expectNoEvents()

            // Window elapsed, so the next value is accepted.
            advanceTimeBy(600)
            runCurrent()
            source.tryEmit(4)
            assertThat(awaitItem()).isEqualTo(4)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `restartWhen runs the upstream only while the trigger is true`() = runTest {
        val online = MutableStateFlow(false)
        var starts = 0
        val upstream = flow {
            starts++
            emit(starts)
        }

        upstream.restartWhen(online).test {
            // Trigger is false: nothing runs at all.
            runCurrent()
            expectNoEvents()
            assertThat(starts).isEqualTo(0)

            online.value = true
            assertThat(awaitItem()).isEqualTo(1)

            // Off and on again restarts it rather than resuming.
            online.value = false
            runCurrent()
            online.value = true
            assertThat(awaitItem()).isEqualTo(2)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `restartWhen ignores repeated identical trigger values`() = runTest {
        val online = MutableStateFlow(true)
        var starts = 0
        val upstream = flow {
            starts++
            emit(starts)
        }

        upstream.restartWhen(online).test {
            assertThat(awaitItem()).isEqualTo(1)

            // Android's connectivity callbacks fire repeatedly for the same state; without
            // distinctUntilChanged inside the operator, each one would restart the upstream.
            online.value = true
            online.value = true
            runCurrent()

            expectNoEvents()
            assertThat(starts).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `startWith emits the initial value before the upstream`() = runTest {
        val values = flowOf("b", "c").startWith("a").toList()

        assertThat(values).containsExactly("a", "b", "c").inOrder()
    }
}
