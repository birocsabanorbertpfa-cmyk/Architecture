package hu.csabi.architecture.data.repository

import hu.csabi.architecture.core.coroutines.AppDispatchers
import hu.csabi.architecture.core.result.AppResult
import hu.csabi.architecture.core.result.map
import hu.csabi.architecture.data.local.RepoDao
import hu.csabi.architecture.data.local.toDomain
import hu.csabi.architecture.data.local.toEntity
import hu.csabi.architecture.data.remote.RepoRemoteDataSource
import hu.csabi.architecture.domain.model.Repo
import hu.csabi.architecture.domain.model.SearchQuery
import hu.csabi.architecture.domain.model.Username
import hu.csabi.architecture.domain.repository.RepoRepository
import javax.inject.Inject
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Lesson 08 — offline-first, with the database as the single source of truth.
 *
 * The rule the whole class follows: **the network writes, the UI reads storage.** A fetched
 * response is never returned straight to the caller; it is written to Room, and Room's
 * observable query pushes it onwards. That one rule buys three things for free:
 *
 *  - the screen works with no connection, because reading never involves the network
 *  - two screens observing the same data can never disagree
 *  - a successful write updates every collector with no manual invalidation
 *
 * Lesson 05's `MutableStateFlow` cache is gone, and the repository interface did not have to
 * change shape for it — it already returned a `Flow`. That was the point of writing it that
 * way a lesson early.
 */
class DefaultRepoRepository @Inject constructor(
    private val remote: RepoRemoteDataSource,
    private val dao: RepoDao,
    private val dispatchers: AppDispatchers,
) : RepoRepository {

    override fun observeSearch(query: SearchQuery): Flow<List<Repo>> =
        dao.observeMatching(query.needle()).map { rows -> rows.map { it.toDomain() } }

    override fun observeCached(): Flow<List<Repo>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun refresh(query: SearchQuery, force: Boolean): AppResult<Unit> =
        withContext(dispatchers.io) {
            // The freshness check is the actual offline-first decision, and it is only
            // possible because the rows carry a `fetched_at` timestamp.
            if (!force && isFresh(query)) return@withContext AppResult.Success(Unit)

            remote.searchRepositories(query)
                .also { result ->
                    if (result is AppResult.Success) {
                        val now = System.currentTimeMillis()
                        dao.upsertAll(result.data.map { it.toEntity(fetchedAt = now) })
                        dao.deleteOlderThan(now - EVICT_AFTER.inWholeMilliseconds)
                    }
                }
                // A failed refresh returns the failure, but changes nothing on disk: the
                // previously stored rows stay visible through observeSearch().
                .map { }
        }

    override suspend fun search(query: SearchQuery, page: Int): AppResult<List<Repo>> =
        withContext(dispatchers.io) {
            refresh(query).map { dao.observeMatching(query.needle()).first().map { it.toDomain() } }
        }

    override suspend fun details(owner: Username, name: String): AppResult<Repo> =
        withContext(dispatchers.io) {
            remote.repoDetails(owner, name)
                .also { result ->
                    if (result is AppResult.Success) {
                        dao.upsertAll(
                            listOf(result.data.toEntity(fetchedAt = System.currentTimeMillis())),
                        )
                    }
                }
        }

    override suspend fun clearCache() = withContext(dispatchers.io) { dao.clear() }

    private suspend fun isFresh(query: SearchQuery): Boolean {
        val newest = dao.newestFetchedAt(query.needle()) ?: return false
        return System.currentTimeMillis() - newest < CACHE_TTL.inWholeMilliseconds
    }

    /**
     * The GitHub query string carries qualifiers (`language:Kotlin stars:>=100`) that SQLite
     * knows nothing about, so local matching uses the free-text term only. Deciding this
     * here, in the data layer, is correct: it is a storage detail, not a business rule.
     */
    private fun SearchQuery.needle(): String = raw.substringBefore(' ')

    private companion object {
        val CACHE_TTL = 5.minutes
        val EVICT_AFTER = 7.days
    }
}
