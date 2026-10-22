package hu.csabi.architecture.testing

import hu.csabi.architecture.domain.model.Repo
import hu.csabi.architecture.domain.model.RepoId
import hu.csabi.architecture.domain.model.Stars
import hu.csabi.architecture.domain.model.Username

/**
 * Lesson 10 — test data builders, so a test states only what it cares about.
 *
 * A test that spells out six constructor arguments buries its intent; `repo(name = "okhttp",
 * stars = 100)` says exactly what matters and nothing else. Defaults also mean adding a
 * field to `Repo` does not break twenty tests.
 */
fun repo(
    id: Long = 1,
    name: String = "repo-$id",
    owner: String = "owner",
    description: String? = "description",
    stars: Int = 100,
    language: String? = "Kotlin",
): Repo = Repo(
    id = RepoId(id),
    name = name,
    owner = Username(owner),
    description = description,
    stars = Stars(stars),
    language = language,
)
