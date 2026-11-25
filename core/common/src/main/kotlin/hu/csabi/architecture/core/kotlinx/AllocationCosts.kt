package hu.csabi.architecture.core.kotlinx

/**
 * Lesson 14 — where Kotlin's zero-cost abstractions stop being zero-cost.
 *
 * The features used throughout this project (value classes, inline functions, sequences)
 * are cheap *under conditions*. Those conditions are written down here, because the failure
 * mode is silent: the code still works, it just allocates in a hot path.
 */

/**
 * ## When a value class actually boxes
 *
 * `@JvmInline value class Stars(val count: Int)` compiles to a bare `int` in the common
 * case. It does **not** in four situations, and each one is visible in the signature:
 *
 * | Situation | Why |
 * |---|---|
 * | `Stars?` | null needs a reference, and `int` has no null |
 * | `List<Stars>` | generics erase to `Object`, so every element boxes |
 * | `Stars` as a type argument anywhere (`Flow<Stars>`, `Map<K, Stars>`) | same erasure |
 * | the class implements an interface and is used *through* it | the call is virtual |
 *
 * The practical rule this project follows: value classes for identifiers and quantities
 * passed as plain parameters, and plain types inside collections that are iterated in a hot
 * path. `RepoEntity` storing `Long`/`Int` rather than `RepoId`/`Stars` (lesson 08) is this
 * rule, not an accident.
 *
 * `Comparable<Stars>` on the value class is the interesting case: `compareTo` boxes when
 * called through `Comparable`, which is why ranking in the use case sorts by
 * `it.stars.count` rather than by `it.stars`.
 */
@Suppress("unused")
private const val VALUE_CLASS_NOTES = "see KDoc"

/**
 * ## What `inline` costs
 *
 * `inline` copies the function body to every call site. For a small function taking a
 * lambda that is a straight win — no `Function1` object, no virtual `invoke`. Three things
 * are worth knowing before reaching for it:
 *
 *  - **bytecode size.** A large inline function called from thirty places is thirty copies.
 *    That is more to verify at class-load time and less likely to stay in the instruction
 *    cache. `inline` on a function with no lambda parameter is almost always pointless, and
 *    the compiler warns about it.
 *  - **binary compatibility.** The body is baked into *callers*, so changing an inline
 *    function in a library does not take effect until every consumer recompiles. For a
 *    published library that is a breaking-change hazard; inside one app it is harmless.
 *  - **`internal` access.** A public inline function may not touch `internal` or `private`
 *    members, because the inlined copy would be outside the module. Lesson 01 hit this with
 *    `searchQuery`, which is why that builder is *not* inline.
 *
 * `noinline` opts one lambda out (needed to store it or pass it on), and `crossinline`
 * forbids a non-local `return` from it — which is required when the lambda is called from
 * another context, such as inside a `launch`.
 */
@Suppress("unused")
private const val INLINE_NOTES = "see KDoc"

/**
 * `crossinline` in practice: the lambda is invoked from inside another lambda, so a non-local
 * return out of [action] would have nowhere to return to. The compiler rejects it rather
 * than generating code that cannot work.
 */
inline fun repeatEach(times: Int, crossinline action: (Int) -> Unit) {
    val runnable = Runnable { for (index in 0 until times) action(index) }
    runnable.run()
}

/**
 * ## Collections: the allocations nobody notices
 *
 * Each of these pairs does the same thing; the right-hand side allocates less.
 */
object CollectionCosts {

    /**
     * `map {}.filter {}` builds two lists. One `mapNotNull` builds one.
     *
     * The chain reads better with two steps and costs an extra list plus a second traversal;
     * at ten elements that is noise, inside a `LazyColumn` item it is not.
     */
    fun <T, R : Any> List<T>.mappedAndFiltered(transform: (T) -> R?): List<R> =
        mapNotNull(transform)

    /**
     * `groupBy` allocates a `List` per key even when each key has exactly one value;
     * `associateBy` allocates none. Using `groupBy` for a unique key is the common slip —
     * the cache in lesson 08 uses `associateBy` for exactly this reason.
     */
    fun <T, K> List<T>.indexedBy(key: (T) -> K): Map<K, T> = associateBy(key)

    /**
     * `first { }` on a `List` is fine; `sortedBy { }.first()` sorts the whole list to find
     * one element. `minByOrNull` is a single pass and no copy.
     */
    fun <T, R : Comparable<R>> List<T>.smallestBy(selector: (T) -> R): T? = minByOrNull(selector)

    /**
     * `+` on a collection copies it. In a loop that is quadratic, which is the single most
     * common accidental performance bug in Kotlin — and it hides behind very readable code.
     * `buildList` mutates one builder and hands back a read-only list.
     */
    fun <T> flatten(chunks: List<List<T>>): List<T> = buildList {
        chunks.forEach { addAll(it) }
    }
}
