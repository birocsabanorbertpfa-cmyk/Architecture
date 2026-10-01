package hu.csabi.architecture.domain.usecase

import hu.csabi.architecture.core.error.AppError
import hu.csabi.architecture.core.result.AppResult
import hu.csabi.architecture.core.result.map
import hu.csabi.architecture.domain.model.Repo
import hu.csabi.architecture.domain.model.SearchQuery
import hu.csabi.architecture.domain.model.searchQuery
import hu.csabi.architecture.domain.repository.RepoRepository
import javax.inject.Inject

/**
 * Lesson 05 — a use case is one business operation, expressed once.
 *
 * The rules below — what counts as a valid query, how results are ordered, what "relevant"
 * means — are business decisions. Putting them in the ViewModel would duplicate them on
 * every screen that searches; putting them in the repository would mix "how do we fetch"
 * with "what do we want". Here they are testable with no Android and no network.
 *
 * `operator fun invoke` lets callers write `searchRepositories(text)`, which reads like the
 * action it performs rather than like an object with a method.
 */
class SearchRepositoriesUseCase @Inject constructor(
    private val repository: RepoRepository,
) {

    suspend operator fun invoke(
        rawQuery: String,
        language: String? = null,
    ): AppResult<List<Repo>> {
        val text = rawQuery.trim()

        // Failing fast without a network round trip is itself a business rule.
        if (text.length < MIN_QUERY_LENGTH) {
            return AppResult.Failure(
                AppError.InvalidInput("Type at least $MIN_QUERY_LENGTH characters"),
            )
        }

        val query: SearchQuery = searchQuery {
            term(text)
            language?.takeIf { it.isNotBlank() }?.let { language(it) }
        }

        return repository.search(query).map { repos -> repos.rank(text) }
    }

    /**
     * Ordering policy: an exact name match first, then popularity. Doing this in the domain
     * means the same ordering applies whether results came from the network or from cache.
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
