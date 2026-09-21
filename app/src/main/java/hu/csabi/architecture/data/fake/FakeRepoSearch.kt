package hu.csabi.architecture.data.fake

import hu.csabi.architecture.core.model.Repo
import hu.csabi.architecture.core.model.RepoId
import hu.csabi.architecture.core.model.SearchQuery
import hu.csabi.architecture.core.model.Stars
import hu.csabi.architecture.core.model.Username
import kotlinx.coroutines.delay
import java.io.IOException
import kotlin.random.Random

/**
 * Lesson 03 — a stand-in data source so the Flow lesson can focus on Flow.
 *
 * Retrofit replaces this in lesson 04 behind the same suspend signature, which is exactly
 * the point: the ViewModel above it will not have to change.
 */
class FakeRepoSearch(
    private val failureRate: Float = 0f,
    private val latencyMillis: Long = 700,
) {

    suspend fun search(query: SearchQuery): List<Repo> {
        delay(latencyMillis)
        if (Random.nextFloat() < failureRate) throw IOException("Network unavailable")

        val needle = query.raw.substringBefore(' ').lowercase()
        return catalogue.filter { repo ->
            needle.isEmpty() ||
                repo.name.lowercase().contains(needle) ||
                repo.language?.lowercase()?.contains(needle) == true
        }
    }

    private companion object {
        val catalogue = listOf(
            repo(1, "compose-samples", "android", "Official Jetpack Compose samples", 21_000, "Kotlin"),
            repo(2, "nowinandroid", "android", "A fully functional Android app", 17_500, "Kotlin"),
            repo(3, "kotlinx.coroutines", "Kotlin", "Coroutines library for Kotlin", 13_400, "Kotlin"),
            repo(4, "retrofit", "square", "A type-safe HTTP client", 43_000, "Java"),
            repo(5, "okhttp", "square", "HTTP client for JVM and Android", 46_200, "Kotlin"),
            repo(6, "dagger", "google", "A fast dependency injector", 17_600, "Java"),
            repo(7, "coil", "coil-kt", "Image loading backed by coroutines", 11_300, "Kotlin"),
            repo(8, "turbine", "cashapp", "Testing library for kotlinx.coroutines Flow", 1_500, "Kotlin"),
        )

        fun repo(id: Long, name: String, owner: String, about: String, stars: Int, lang: String) =
            Repo(RepoId(id), name, Username(owner), about, Stars(stars), lang)
    }
}
