package hu.csabi.architecture.data.fake

import hu.csabi.architecture.core.error.AppError
import hu.csabi.architecture.core.result.AppResult
import hu.csabi.architecture.data.remote.RepoRemoteDataSource
import hu.csabi.architecture.domain.model.Repo
import hu.csabi.architecture.domain.model.RepoId
import hu.csabi.architecture.domain.model.RepoPage
import hu.csabi.architecture.domain.model.SearchQuery
import hu.csabi.architecture.domain.model.Stars
import hu.csabi.architecture.domain.model.Username
import java.io.IOException
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * Lesson 05 — the fake is not a test-only detail, it is the proof the seam works.
 *
 * It implements the same [RepoRemoteDataSource] port as the Retrofit-backed one, so the
 * repository above cannot tell them apart. Swapping them is a one-line change in the
 * composition root, which is what makes offline development and lesson 10's tests cheap.
 *
 * It also returns failures as [AppResult.Failure] rather than throwing, matching the real
 * implementation's contract instead of a convenient approximation of it.
 */
class FakeRemoteDataSource(
    private val failureRate: Float = 0f,
    private val latencyMillis: Long = 700,
) : RepoRemoteDataSource {

    override suspend fun searchRepositories(
        query: SearchQuery,
        page: Int,
    ): AppResult<List<Repo>> {
        delay(latencyMillis)
        if (Random.nextFloat() < failureRate) {
            return AppResult.Failure(AppError.Network(IOException("Simulated network failure")))
        }

        val needle = query.raw.substringBefore(' ').lowercase()
        val matches = catalogue.filter { repo ->
            needle.isEmpty() ||
                repo.name.lowercase().contains(needle) ||
                repo.language?.lowercase()?.contains(needle) == true
        }
        return AppResult.Success(matches)
    }

    /**
     * Lesson 09 — the fake has to page too, otherwise it stops being a faithful stand-in.
     * Slicing the catalogue is enough to exercise the mediator's end-of-pagination logic.
     */
    override suspend fun searchRepositoriesPage(
        query: SearchQuery,
        page: Int,
        perPage: Int,
    ): AppResult<RepoPage> = when (val all = searchRepositories(query, page)) {
        is AppResult.Failure -> all
        is AppResult.Success -> {
            val from = (page - 1) * perPage
            val slice = all.data.drop(from).take(perPage)
            AppResult.Success(RepoPage(repos = slice, totalCount = all.data.size))
        }
    }

    override suspend fun repoDetails(owner: Username, name: String): AppResult<Repo> {
        delay(latencyMillis)
        val match = catalogue.firstOrNull { it.owner == owner && it.name == name }
        return match
            ?.let { AppResult.Success(it) }
            ?: AppResult.Failure(AppError.Http(code = 404, body = null))
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
