package hu.csabi.architecture.domain.usecase

import hu.csabi.architecture.core.error.AppError
import hu.csabi.architecture.core.result.AppResult
import hu.csabi.architecture.core.result.map
import hu.csabi.architecture.domain.model.Repo
import hu.csabi.architecture.domain.model.SearchQuery
import hu.csabi.architecture.domain.model.searchQuery
import hu.csabi.architecture.domain.repository.RepoRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Lesson 05 — a use case is one business operation, expressed once.
 *
 * The rules here — what counts as a valid query, how results are ordered — are business
 * decisions. In a ViewModel they would be duplicated on every screen that searches; in the
 * repository they would mix "how do we fetch" with "what do we want". Here they are testable
 * with no Android and no network.
 *
 * Lesson 08 split it into [observe] and [refresh], mirroring the repository: reading is a
 * stream from storage that cannot fail, refreshing is a one-shot that can. The ordering rule
 * is applied on the *stream*, so cached and freshly fetched results are ranked identically.
 */
class SearchRepositoriesUseCase @Inject constructor(
    private val repository: RepoRepository,
) {

    /** The stream a screen renders. An invalid query yields an empty list, not an error. */
    fun observe(rawQuery: String, language: String? = null): Flow<List<Repo>> {
        val text = rawQuery.trim()
        if (text.length < MIN_QUERY_LENGTH) return flowOf(emptyList())

        return repository.observeSearch(buildQuery(text, language))
            .map { repos -> repos.rank(text) }
    }

    /** Fetch and store. Returns whether the fetch worked; the data arrives via [observe]. */
    suspend fun refresh(
        rawQuery: String,
        language: String? = null,
        force: Boolean = false,
    ): AppResult<Unit> {
        val text = rawQuery.trim()

        // Failing fast without a network round trip is itself a business rule.
        if (text.length < MIN_QUERY_LENGTH) {
            return AppResult.Failure(
                AppError.InvalidInput("Type at least $MIN_QUERY_LENGTH characters"),
            )
        }
        return repository.refresh(buildQuery(text, language), force = force)
    }

    /**
     * One-shot form for callers that are not observing a stream.
     *
     * `operator fun invoke` lets them write `searchRepositories(text)`, which reads like the
     * action it performs rather than like an object with a method.
     */
    suspend operator fun invoke(
        rawQuery: String,
        language: String? = null,
    ): AppResult<List<Repo>> {
        val text = rawQuery.trim()
        if (text.length < MIN_QUERY_LENGTH) {
            return AppResult.Failure(
                AppError.InvalidInput("Type at least $MIN_QUERY_LENGTH characters"),
            )
        }
        return repository.search(buildQuery(text, language)).map { it.rank(text) }
    }

    private fun buildQuery(text: String, language: String?): SearchQuery = searchQuery {
        term(text)
        language?.takeIf { it.isNotBlank() }?.let { language(it) }
    }

    /**
     * Ordering policy: an exact name match first, then popularity. Living in the domain means
     * the same order applies whether the rows came from the network or from disk.
     */
    private fun List<Repo>.rank(text: String): List<Repo> =
        sortedWith(
            compareByDescending<Repo> { it.name.equals(text, ignoreCase = true) }
                .thenByDescending { it.stars.count },
        )

    private companion object {
        const val MIN_QUERY_LENGTH = 2
    }
}
