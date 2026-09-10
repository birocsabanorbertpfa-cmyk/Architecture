package hu.csabi.architecture.core.kotlinx

/**
 * Lesson 01 — Sequence vs. List.
 *
 * `List.map{}.filter{}.take(n)` minden lépésnél új listát allokál és a teljes
 * kollekciót végigjárja. A `Sequence` lusta: elemenként megy végig a láncon, és
 * a `take(n)` után abbahagyja. Nagy listánál / drága transzformációnál ez a nyerő,
 * kicsinél viszont a Sequence overhead-je lassabb — nem dogma, hanem mérés kérdése.
 */
fun <T, R : Any> List<T>.firstMappedOrNull(limit: Int, transform: (T) -> R?): List<R> =
    asSequence()
        .mapNotNull(transform)
        .take(limit)
        .toList()

/**
 * `buildList`: egy mutable buildert ad kölcsön, a végén read-only listát ad vissza.
 * Nincs `toList()` másolás, és a hívó nem tudja mutálni az eredményt.
 */
fun formatQualifiers(values: Map<String, String>): List<String> = buildList(values.size) {
    for ((key, value) in values) {
        if (value.isNotBlank()) add("$key:$value")
    }
}
