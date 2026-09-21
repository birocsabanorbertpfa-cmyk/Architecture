package hu.csabi.architecture.core.model

/**
 * Lesson 03 — the domain model the whole app will be built around.
 *
 * It is a plain Kotlin class with no framework types: no JSON annotations, no Room entity,
 * no Compose state. Lesson 04 adds a network DTO that maps *into* this, and lesson 08 a
 * database entity that maps both ways. Keeping this layer free of dependencies is what
 * makes those later steps additive instead of invasive.
 *
 * Note the value classes from lesson 01: `RepoId` and `Stars` cannot be mixed up here,
 * even though both are numbers underneath.
 */
data class Repo(
    val id: RepoId,
    val name: String,
    val owner: Username,
    val description: String?,
    val stars: Stars,
    val language: String?,
) {
    val fullName: String get() = "${owner.value}/$name"
}
