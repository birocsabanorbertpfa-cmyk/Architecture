package hu.csabi.architecture.domain.repository

import hu.csabi.architecture.core.result.AppResult
import hu.csabi.architecture.domain.model.Repo
import hu.csabi.architecture.domain.model.SearchQuery
import hu.csabi.architecture.domain.model.Username
import kotlinx.coroutines.flow.Flow

/**
 * Lesson 05 — the contract lives in the domain, the implementation lives in data.
 *
 * This is dependency inversion, and it is the reason the arrow points the way it does:
 * `data` depends on `domain`, never the other way round. The domain layer can be compiled,
 * read and tested without knowing that Retrofit, Room or GitHub exist.
 *
 * Notice what the signatures do *not* mention: no DTO, no HTTP status, no `Response<T>`,
 * no threading. Only domain types and [AppResult].
 */
interface RepoRepository {

    /** One-shot search. Failures arrive as values, never as thrown exceptions. */
    suspend fun search(query: SearchQuery, page: Int = 1): AppResult<List<Repo>>

    suspend fun details(owner: Username, name: String): AppResult<Repo>

    /**
     * Everything the repository has seen so far, as a stream.
     *
     * Returning a `Flow` instead of a `List` is what makes the repository a *single source
     * of truth*: callers observe it and are pushed updates, rather than asking again after
     * every write. Lesson 08 swaps the backing store for Room without changing this line.
     */
    fun observeCached(): Flow<List<Repo>>

    suspend fun clearCache()
}
