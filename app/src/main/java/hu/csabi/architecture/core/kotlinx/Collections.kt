package hu.csabi.architecture.core.kotlinx

/**
 * Lesson 01 — Sequence vs List.
 *
 * `list.map{}.filter{}.take(n)` allocates an intermediate list per step and walks the whole
 * collection every time. A `Sequence` is lazy: it pushes one element through the whole
 * chain and stops as soon as `take(n)` is satisfied. That wins on large inputs or expensive
 * transforms — on small collections the sequence overhead makes it slower, so measure
 * instead of treating it as a rule.
 */
fun <T, R : Any> List<T>.firstMappedOrNull(limit: Int, transform: (T) -> R?): List<R> =
    asSequence()
        .mapNotNull(transform)
        .take(limit)
        .toList()

/**
 * `buildList` lends a mutable builder and returns a read-only list: no defensive `toList()`
 * copy, and the caller cannot mutate the result.
 */
fun formatQualifiers(values: Map<String, String>): List<String> = buildList(values.size) {
    for ((key, value) in values) {
        if (value.isNotBlank()) add("$key:$value")
    }
}
