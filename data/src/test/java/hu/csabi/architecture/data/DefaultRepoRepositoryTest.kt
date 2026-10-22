package hu.csabi.architecture.data

import com.google.common.truth.Truth.assertThat
import hu.csabi.architecture.core.error.AppError
import hu.csabi.architecture.core.result.AppResult
import hu.csabi.architecture.data.local.ArchitectureDatabase
import hu.csabi.architecture.data.local.toEntity
import hu.csabi.architecture.data.remote.RepoRemoteDataSource
import hu.csabi.architecture.data.repository.DefaultRepoRepository
import hu.csabi.architecture.domain.model.SearchQuery
import hu.csabi.architecture.testing.FakeRepoDao
import hu.csabi.architecture.testing.TestAppDispatchers
import hu.csabi.architecture.testing.repo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Lesson 10 — mock *and* fake in the same test, each where it fits.
 *
 * - the remote data source is a **mock**: these tests are about whether the network is
 *   called, with what, and how often — pure interaction assertions
 * - the DAO is a **fake**: the tests are about what ends up stored and what observers see,
 *   which is state
 *
 * Reversing the two would make both halves worse: a mocked DAO cannot tell you "the failed
 * refresh left the old rows alone", and a faked network cannot tell you "the second call was
 * skipped".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultRepoRepositoryTest {

    private val dispatcher = StandardTestDispatcher()
    private val remote = mockk<RepoRemoteDataSource>()
    private val dao = FakeRepoDao()

    /**
     * Only `pagedSearch` touches the database object, and these tests do not call it. A
     * relaxed mock documents that honestly instead of constructing a real Room instance the
     * test would never use.
     */
    private val database = mockk<ArchitectureDatabase>(relaxed = true)

    private val repository = DefaultRepoRepository(
        remote = remote,
        dao = dao,
        database = database,
        dispatchers = TestAppDispatchers(dispatcher),
    )

    private val query = SearchQuery("okhttp")

    @Test
    fun `a successful refresh stores the rows and observers see them`() = runTest(dispatcher) {
        val fetched = listOf(repo(id = 1, name = "okhttp", stars = 46_000))
        coEvery { remote.searchRepositories(query, any()) } returns AppResult.Success(fetched)

        val result = repository.refresh(query)

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        assertThat(repository.observeSearch(query).first().map { it.name }).containsExactly("okhttp")
    }

    @Test
    fun `a second refresh inside the cache window does not hit the network`() =
        runTest(dispatcher) {
            coEvery { remote.searchRepositories(query, any()) } returns
                AppResult.Success(listOf(repo(id = 1, name = "okhttp")))

            repository.refresh(query)
            repository.refresh(query)

            // The freshness check is the whole point of the fetched_at column.
            coVerify(exactly = 1) { remote.searchRepositories(query, any()) }
        }

    @Test
    fun `force bypasses the freshness check`() = runTest(dispatcher) {
        coEvery { remote.searchRepositories(query, any()) } returns
            AppResult.Success(listOf(repo(id = 1, name = "okhttp")))

        repository.refresh(query)
        repository.refresh(query, force = true)

        coVerify(exactly = 2) { remote.searchRepositories(query, any()) }
    }

    @Test
    fun `a failed refresh returns the failure and leaves stored rows untouched`() =
        runTest(dispatcher) {
            dao.seed(listOf(repo(id = 1, name = "okhttp").toEntity(fetchedAt = 0)))
            coEvery { remote.searchRepositories(query, any()) } returns
                AppResult.Failure(AppError.Network(cause = null))

            val result = repository.refresh(query)

            assertThat(result).isInstanceOf(AppResult.Failure::class.java)
            // Offline-first in one assertion: the error is reported, the data survives.
            assertThat(repository.observeSearch(query).first().map { it.name })
                .containsExactly("okhttp")
        }

    @Test
    fun `search reads from storage, not from the network response`() = runTest(dispatcher) {
        // A row the network does not return, but storage holds. If search() returned the
        // response directly, this row would be missing — and the single source of truth
        // would be a slogan rather than a property.
        //
        // The timestamp has to sit in a specific window, and getting it wrong is instructive:
        // `fetchedAt = 0` would be evicted by the refresh (older than the eviction cutoff),
        // while a timestamp of "now" would count as fresh and skip the network entirely. An
        // hour old is stale enough to refresh, recent enough to survive.
        val anHourAgo = System.currentTimeMillis() - 60 * 60 * 1000
        dao.seed(
            listOf(repo(id = 99, name = "okhttp-legacy", stars = 1).toEntity(fetchedAt = anHourAgo)),
        )
        coEvery { remote.searchRepositories(query, any()) } returns
            AppResult.Success(listOf(repo(id = 1, name = "okhttp", stars = 46_000)))

        val result = repository.search(query)

        val names = (result as AppResult.Success).data.map { it.name }
        assertThat(names).containsExactly("okhttp", "okhttp-legacy")
    }

    @Test
    fun `stale rows are evicted on a successful refresh`() = runTest(dispatcher) {
            val ancient = repo(id = 50, name = "okhttp-ancient").toEntity(fetchedAt = 1)
            dao.seed(listOf(ancient))
            coEvery { remote.searchRepositories(query, any()) } returns
                AppResult.Success(listOf(repo(id = 1, name = "okhttp")))

            repository.refresh(query, force = true)

            assertThat(dao.stored.map { it.name }).doesNotContain("okhttp-ancient")
        }

    @Test
    fun `clearing the cache empties storage`() = runTest(dispatcher) {
        dao.seed(listOf(repo(id = 1, name = "okhttp").toEntity(fetchedAt = 0)))

        repository.clearCache()

        assertThat(dao.stored).isEmpty()
    }
}
