package hu.csabi.architecture.testing

import androidx.paging.PagingData
import hu.csabi.architecture.core.error.AppError
import hu.csabi.architecture.core.result.AppResult
import hu.csabi.architecture.domain.model.Repo
import hu.csabi.architecture.domain.model.SearchQuery
import hu.csabi.architecture.domain.model.Username
import hu.csabi.architecture.domain.repository.RepoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Lesson 10 — a hand-written fake, and why it beats a mock here.
 *
 * The rule of thumb this project follows:
 *  - **fake** when the collaborator holds *state* (a repository, a database, a cache)
 *  - **mock** when the assertion is about an *interaction* ("was the network called at all?")
 *
 * A mocked repository forces every test to re-stub the whole contract, and the stubbing says
 * nothing about whether the pieces fit together: `coEvery { observeSearch(any()) }` happily
 * returns rows that `refresh` never stored. This fake behaves like the real thing — writing
 * through `refresh` is what makes rows appear in `observeSearch` — so a test can describe a
 * scenario instead of a sequence of return values.
 *
 * It is also the only place that needs updating when the contract grows, instead of every
 * test file.
 */
class FakeRepoRepository : RepoRepository {

    private val stored = MutableStateFlow<List<Repo>>(emptyList())

    /** What the next [refresh] will do. Tests set this to describe the scenario. */
    var nextRefreshResult: AppResult<Unit> = AppResult.Success(Unit)

    /** What [refresh] will store on success. */
    var remoteRepos: List<Repo> = emptyList()

    /** Observable interactions, for the few assertions that are genuinely about calls. */
    var refreshCount: Int = 0
        private set
    var lastForce: Boolean? = null
        private set
    var lastQuery: SearchQuery? = null
        private set

    fun failNextRefresh(error: AppError) {
        nextRefreshResult = AppResult.Failure(error)
    }

    /** Pre-populate storage, as if a previous session had cached these. */
    fun seed(repos: List<Repo>) {
        stored.value = repos
    }

    override fun observeSearch(query: SearchQuery): Flow<List<Repo>> {
        val needle = query.raw.substringBefore(' ').lowercase()
        return stored.map { repos ->
            repos.filter { needle.isEmpty() || it.name.lowercase().contains(needle) }
        }
    }

    override suspend fun refresh(query: SearchQuery, force: Boolean): AppResult<Unit> {
        refreshCount++
        lastForce = force
        lastQuery = query
        val result = nextRefreshResult
        // Only a successful refresh writes — the behaviour the real repository has, and the
        // reason "failed refresh keeps showing cached rows" is testable at all.
        if (result is AppResult.Success) stored.value = remoteRepos
        return result
    }

    override suspend fun search(query: SearchQuery, page: Int): AppResult<List<Repo>> =
        when (val refreshed = refresh(query)) {
            is AppResult.Failure -> refreshed
            is AppResult.Success -> AppResult.Success(stored.value)
        }

    override fun pagedSearch(query: SearchQuery): Flow<PagingData<Repo>> =
        flowOf(PagingData.from(stored.value))

    override suspend fun details(owner: Username, name: String): AppResult<Repo> =
        stored.value.firstOrNull { it.owner == owner && it.name == name }
            ?.let { AppResult.Success(it) }
            ?: AppResult.Failure(AppError.Http(code = 404, body = null))

    override fun observeCached(): Flow<List<Repo>> = stored

    override suspend fun clearCache() {
        stored.value = emptyList()
    }
}
