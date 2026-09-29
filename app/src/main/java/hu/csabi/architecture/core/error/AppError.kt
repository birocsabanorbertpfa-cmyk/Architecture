package hu.csabi.architecture.core.error

/**
 * Lesson 04 — a typed error hierarchy.
 *
 * Retrofit, OkHttp and kotlinx.serialization each throw their own exception types. Letting
 * those reach the ViewModel would leak the network library into the UI layer: the screen
 * would have to know what an `HttpException` is. Everything is mapped to this hierarchy at
 * the edge of the data layer instead, so upper layers `when` over a closed set of cases
 * they can actually act on.
 *
 * It extends `Exception` so a mapped error can still be thrown where a throwable is
 * required. Lesson 05 narrowed `AppResult.Failure` from `Throwable` to `AppError`, which
 * was a one-line change precisely because the mapping already happened here.
 */
sealed class AppError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** No usable connection: airplane mode, DNS failure, timeout. Retrying may help. */
    class Network(cause: Throwable?) : AppError("Network unavailable", cause)

    /** The server answered, but with a failure status. */
    class Http(val code: Int, val body: String?) : AppError("HTTP $code")

    /** GitHub allows 60 unauthenticated requests per hour; [resetAtEpochSeconds] says when. */
    class RateLimited(val resetAtEpochSeconds: Long?) : AppError("API rate limit exceeded")

    /** The response did not match the DTO: a backend change, or an HTML error page. */
    class Serialization(cause: Throwable?) : AppError("Unexpected response format", cause)

    /**
     * The request never left the app: a use case rejected the input. Lesson 05 added this
     * case, and the compiler then pointed at every `when` that had to handle it.
     */
    class InvalidInput(val reason: String) : AppError(reason)

    /** Anything not recognised. Worth logging, never worth guessing about. */
    class Unknown(cause: Throwable?) : AppError(cause?.message ?: "Unknown error", cause)

    /** Retrying the same call is only sensible for transient failures. */
    val isRetryable: Boolean
        get() = when (this) {
            is Network -> true
            is Http -> code >= 500
            is RateLimited, is Serialization, is InvalidInput, is Unknown -> false
        }
}
