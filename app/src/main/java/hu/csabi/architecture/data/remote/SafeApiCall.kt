package hu.csabi.architecture.data.remote

import hu.csabi.architecture.core.error.AppError
import hu.csabi.architecture.core.result.AppResult
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

private const val HTTP_FORBIDDEN = 403
private const val HTTP_TOO_MANY_REQUESTS = 429

/**
 * Lesson 04 — the single place where exceptions become values.
 *
 * Retrofit signals failure by throwing, which is fine at the edge but hostile further up:
 * the caller cannot tell from the signature that anything may go wrong. This funnel turns
 * every known failure into an [AppError] inside an [AppResult], so the rest of the app
 * handles errors with `when` instead of `try`.
 *
 * `CancellationException` is rethrown first and deliberately: it is not a failure, it is the
 * mechanism coroutine cancellation is built on (lesson 02).
 */
suspend fun <T> safeApiCall(block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (http: HttpException) {
    AppResult.Failure(http.toAppError())
} catch (io: IOException) {
    // Covers no connectivity, DNS failures and every timeout OkHttp reports.
    AppResult.Failure(AppError.Network(io))
} catch (serialization: SerializationException) {
    AppResult.Failure(AppError.Serialization(serialization))
} catch (throwable: Throwable) {
    AppResult.Failure(AppError.Unknown(throwable))
}

/**
 * GitHub reports a spent quota as 403 with `X-RateLimit-Remaining: 0` — indistinguishable
 * from a genuine permission error unless the header is read. Treating the two the same way
 * would tell the user "forbidden" when the real answer is "wait 14 minutes".
 */
private fun HttpException.toAppError(): AppError {
    val response = response()
    val headers = response?.headers()
    val remaining = headers?.get("X-RateLimit-Remaining")?.toIntOrNull()
    val resetAt = headers?.get("X-RateLimit-Reset")?.toLongOrNull()

    val rateLimited = code() == HTTP_TOO_MANY_REQUESTS ||
        (code() == HTTP_FORBIDDEN && remaining == 0)

    return if (rateLimited) {
        AppError.RateLimited(resetAt)
    } else {
        AppError.Http(code = code(), body = response?.errorBody()?.string())
    }
}
