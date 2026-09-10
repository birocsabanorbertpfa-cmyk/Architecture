package hu.csabi.architecture.core.result

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Lesson 01 — sealed interface + generic variance.
 *
 * `out T` makes the type covariant, so an `AppResult<Repo>` is usable where an
 * `AppResult<Any>` is expected. `Failure` is an `AppResult<Nothing>`, and since
 * `Nothing` is a subtype of every type, a single `Failure` instance fits into any
 * `AppResult<T>` — no type argument juggling on the error branch.
 *
 * Sealed *interface* rather than class: it can take part in several hierarchies, and
 * `when` stays exhaustive — no `else` branch, and a new subtype breaks compilation
 * everywhere it needs handling.
 */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: Throwable) : AppResult<Nothing>
}

@OptIn(ExperimentalContracts::class)
fun <T> AppResult<T>.isSuccess(): Boolean {
    contract { returns(true) implies (this@isSuccess is AppResult.Success<T>) }
    return this is AppResult.Success
}

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.data

fun <T> AppResult<T>.errorOrNull(): Throwable? = (this as? AppResult.Failure)?.error

/**
 * `inline` + lambda: the body of `transform` is copied to the call site, so there is no
 * `Function1` allocation and no extra stack frame. This is the right default for small
 * functions taking a lambda — but not for large bodies, where the bytecode would be
 * duplicated at every call site.
 */
inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Failure -> this // AppResult<Nothing> is also an AppResult<R>
}

inline fun <T, R> AppResult<T>.flatMap(transform: (T) -> AppResult<R>): AppResult<R> = when (this) {
    is AppResult.Success -> transform(data)
    is AppResult.Failure -> this
}

@OptIn(ExperimentalContracts::class)
inline fun <T> AppResult<T>.onFailure(action: (Throwable) -> Unit): AppResult<T> {
    contract { callsInPlace(action, InvocationKind.AT_MOST_ONCE) }
    if (this is AppResult.Failure) action(error)
    return this
}

/**
 * `reified` keeps the type argument available at runtime, so `error is E` compiles into a
 * real `instanceof`. Without `inline` + `reified`, type erasure would make this impossible.
 */
inline fun <reified E : Throwable, T> AppResult<T>.recover(fallback: (E) -> T): AppResult<T> =
    when (this) {
        is AppResult.Success -> this
        is AppResult.Failure -> if (error is E) AppResult.Success(fallback(error)) else this
    }

/**
 * Unlike stdlib `runCatching`, this rethrows `CancellationException`.
 *
 * Coroutine cancellation is implemented by throwing it; swallowing it would keep a
 * cancelled coroutine running and surface a bogus error on screen. See lesson 02.
 */
inline fun <T> appRunCatching(block: () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (cancellation: kotlin.coroutines.cancellation.CancellationException) {
    throw cancellation
} catch (throwable: Throwable) {
    AppResult.Failure(throwable)
}
