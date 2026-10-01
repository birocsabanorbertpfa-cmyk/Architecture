package hu.csabi.architecture.data.repository

import hu.csabi.architecture.core.coroutines.AppDispatchers
import hu.csabi.architecture.core.result.AppResult
import hu.csabi.architecture.data.remote.RepoRemoteDataSource
import hu.csabi.architecture.domain.model.Repo
import hu.csabi.architecture.domain.model.RepoId
import hu.csabi.architecture.domain.model.SearchQuery
import hu.csabi.architecture.domain.model.Username
import hu.csabi.architecture.domain.repository.RepoRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Lesson 05 — the implementation side of the contract.
 *
 * Its job is coordination, not transport: decide where data comes from, keep a single
 * source of truth, and hand the domain exactly what it asked for. The in-memory cache here
 * is deliberately simple — lesson 08 replaces it with Room, and because the repository
 * interface already returns a `Flow`, that swap does not reach the ViewModel.
 */
class DefaultRepoRepository @Inject constructor(
    private val remote: RepoRemoteDataSource,
    private val dispatchers: AppDispatchers,
) : RepoRepository {

    /**
     * The cache is keyed by repo id, so results from different queries merge instead of
     * overwriting each other — the shape a database would give us anyway.
     */
    private val cache = MutableStateFlow<Map<RepoId, Repo>>(emptyMap())

    /**
     * A `Mutex` rather than `synchronized`: locking must not block a thread in coroutine
     * code. `withLock` suspends instead, so the thread stays available for other work.
     */
    private val cacheMutex = Mutex()

    override suspend fun search(query: SearchQuery, page: Int): AppResult<List<Repo>> =
        withContext(dispatchers.io) {
            // A failed refresh is not the same as having no data: the failure is returned,
            // but whatever was cached earlier stays observable through observeCached().
            remote.searchRepositories(query, page)
                .also { result -> if (result is AppResult.Success) cache(result.data) }
        }

    override suspend fun details(owner: Username, name: String): AppResult<Repo> =
        withContext(dispatchers.io) {
            remote.repoDetails(owner, name)
                .also { result -> if (result is AppResult.Success) cache(listOf(result.data)) }
        }

    override fun observeCached(): Flow<List<Repo>> =
        cache.asStateFlow().map { byId -> byId.values.sortedByDescending { it.stars.count } }

    override suspend fun clearCache() = cacheMutex.withLock {
        cache.value = emptyMap()
    }

    private suspend fun cache(repos: List<Repo>) = cacheMutex.withLock {
        cache.value = cache.value + repos.associateBy { it.id }
    }
}
