package hu.csabi.architecture.core.result

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Lesson 01 — sealed interface + generikus variancia.
 *
 * `out T`: kovariáns, ezért `AppResult<Repo>` átadható `AppResult<Any>` helyére.
 * A `Failure` `AppResult<Nothing>`, így minden `AppResult<T>` altípusa — nem kell
 * típusparaméterrel bajlódni hibaágon.
 *
 * Sealed interface (nem class): több hierarchiába is beilleszthető, és a `when`
 * kimerítő marad — nincs `else` ág, új altípus fordítási hibát ad.
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
 * `inline` + lambda: a `transform` a hívás helyére kerül, nincs Function objektum
 * allokáció és nincs extra stack frame. Ezért használunk `inline`-t rövid,
 * lambdát fogadó függvényeknél.
 */
inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Failure -> this
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
 * `reified`: futásidőben is megvan a típus, így `is T` írható — `inline` nélkül
 * a type erasure miatt ez lehetetlen lenne.
 */
inline fun <reified E : Throwable, T> AppResult<T>.recover(fallback: (E) -> T): AppResult<T> =
    when (this) {
        is AppResult.Success -> this
        is AppResult.Failure -> if (error is E) AppResult.Success(fallback(error)) else this
    }

/**
 * A `CancellationException` továbbdobása kulcsfontosságú lesz a 02. leckében:
 * ha elnyeljük, a coroutine cancellation elromlik.
 */
inline fun <T> appRunCatching(block: () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (cancellation: kotlin.coroutines.cancellation.CancellationException) {
    throw cancellation
} catch (throwable: Throwable) {
    AppResult.Failure(throwable)
}
