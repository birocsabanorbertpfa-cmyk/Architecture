package hu.csabi.architecture.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import hu.csabi.architecture.core.coroutines.AppDispatchers
import hu.csabi.architecture.core.result.AppResult
import hu.csabi.architecture.core.result.map
import hu.csabi.architecture.data.local.ArchitectureDatabase
import hu.csabi.architecture.data.local.RepoDao
import hu.csabi.architecture.data.local.localNeedle
import hu.csabi.architecture.data.paging.RepoRemoteMediator
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
    private val database: ArchitectureDatabase,
    private val dispatchers: AppDispatchers,
) : RepoRepository {

    override fun observeSearch(query: SearchQuery): Flow<List<Repo>> =
        dao.observeMatching(query.localNeedle()).map { rows -> rows.map { it.toDomain() } }

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
            refresh(query).map { dao.observeMatching(query.localNeedle()).first().map { it.toDomain() } }
        }

    /**
     * Lesson 09 — assembling the pager.
     *
     * Two details that bite people:
     *  - `initialLoadSize` defaults to **three times** `pageSize`, which quietly breaks a
     *    mediator whose page arithmetic assumes equal pages. Setting them equal keeps
     *    "page N contains items N*size..." true.
     *  - `enablePlaceholders = false` because the DAO does not report a total count to
     *    Paging; with placeholders on, the list would show null items it can never fill.
     *
     * `PagingData.map` converts entities to domain models **per loaded page**, so nothing
     * maps a list it has not displayed.
     */
    @OptIn(ExperimentalPagingApi::class)
    override fun pagedSearch(query: SearchQuery): Flow<PagingData<Repo>> = Pager(
        config = PagingConfig(
            pageSize = RepoRemoteMediator.PAGE_SIZE,
            initialLoadSize = RepoRemoteMediator.PAGE_SIZE,
            prefetchDistance = PREFETCH_DISTANCE,
            enablePlaceholders = false,
        ),
        remoteMediator = RepoRemoteMediator(query, remote, database),
        pagingSourceFactory = { dao.pagingSource(query.localNeedle()) },
    ).flow.map { page -> page.map { it.toDomain() } }

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
        val newest = dao.newestFetchedAt(query.localNeedle()) ?: return false
        return System.currentTimeMillis() - newest < CACHE_TTL.inWholeMilliseconds
    }

    /**
     * The GitHub query string carries qualifiers (`language:Kotlin stars:>=100`) that SQLite
     * knows nothing about, so local matching uses the free-text term only. Deciding this
     * here, in the data layer, is correct: it is a storage detail, not a business rule.
     */
    private companion object {
        const val PREFETCH_DISTANCE = 5
        val CACHE_TTL = 5.minutes
        val EVICT_AFTER = 7.days
    }
}
