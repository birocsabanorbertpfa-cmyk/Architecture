package hu.csabi.architecture.core

import hu.csabi.architecture.core.error.AppError
import hu.csabi.architecture.core.result.AppResult
import hu.csabi.architecture.core.result.appRunCatching
import hu.csabi.architecture.core.result.errorOrNull
import hu.csabi.architecture.core.result.flatMap
import hu.csabi.architecture.core.result.getOrNull
import hu.csabi.architecture.core.result.map
import hu.csabi.architecture.core.result.recover
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppResultTest {

    @Test
    fun `map transforms success and keeps failure`() {
        val success: AppResult<Int> = AppResult.Success(2)
        val failure: AppResult<Int> = AppResult.Failure(AppError.Unknown(IllegalStateException("boom")))

        assertEquals(4, success.map { it * 2 }.getOrNull())
        assertNull(failure.map { it * 2 }.getOrNull())
    }

    @Test
    fun `flatMap chains without nesting`() {
        val result = AppResult.Success(10).flatMap { value ->
            if (value > 5) AppResult.Success(value.toString()) else AppResult.Failure(AppError.Unknown(AssertionError()))
        }

        assertEquals("10", result.getOrNull())
    }

    @Test
    fun `recover only catches the reified error type`() {
        val failure: AppResult<Int> = AppResult.Failure(AppError.InvalidInput("bad input"))

        assertEquals(0, failure.recover<AppError.InvalidInput, Int> { 0 }.getOrNull())
        assertNull(failure.recover<AppError.Network, Int> { -1 }.getOrNull())
    }

    @Test
    fun `appRunCatching keeps an AppError as is and wraps everything else`() {
        val mapped = appRunCatching { throw AppError.RateLimited(resetAtEpochSeconds = 1L) }
        val wrapped = appRunCatching { error("boom") }

        assertTrue(mapped.errorOrNull() is AppError.RateLimited)
        assertTrue(wrapped.errorOrNull() is AppError.Unknown)
    }

    @Test
    fun `appRunCatching wraps thrown exceptions`() {
        val result = appRunCatching { error("nope") }

        assertTrue(result.errorOrNull() is AppError.Unknown)
    }

    @Test(expected = CancellationException::class)
    fun `appRunCatching rethrows cancellation`() {
        appRunCatching { throw CancellationException("cancelled") }
    }
}
