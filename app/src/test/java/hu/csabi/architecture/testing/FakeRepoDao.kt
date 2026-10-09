package hu.csabi.architecture.testing

import androidx.paging.PagingSource
import hu.csabi.architecture.data.local.RepoDao
import hu.csabi.architecture.data.local.RepoEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Lesson 10 — an in-memory stand-in for the DAO.
 *
 * Room's generated DAO needs real SQLite, which on the JVM means Robolectric (slow) or an
 * instrumented test (slower). For testing the *repository's* logic — freshness, what gets
 * stored, what a failure leaves behind — none of that is needed: the DAO is an interface, so
 * a map behind a `MutableStateFlow` reproduces everything the repository relies on, including
 * the fact that a write pushes to observers.
 *
 * The queries in the real DAO are still covered, just by the right kind of test: the
 * instrumented ones that run against actual SQLite.
 */
class FakeRepoDao : RepoDao {

    private val rows = MutableStateFlow<Map<Long, RepoEntity>>(emptyMap())

    val stored: List<RepoEntity> get() = rows.value.values.toList()

    fun seed(entities: List<RepoEntity>) {
        rows.value = entities.associateBy { it.id }
    }

    override fun observeMatching(needle: String): Flow<List<RepoEntity>> =
        rows.map { byId -> byId.values.filter { it.matches(needle) }.sortedByDescending { it.stars } }

    override fun observeAll(): Flow<List<RepoEntity>> =
        rows.map { byId -> byId.values.sortedByDescending { it.stars } }

    /**
     * Not implemented on purpose. Paging is covered by the mediator's own tests and by
     * instrumented tests; a hand-rolled `PagingSource` here would be a second implementation
     * of Room's, and a test asserting against *that* would prove nothing about production.
     * Failing loudly is better than a quietly wrong fake.
     */
    override fun pagingSource(needle: String): PagingSource<Int, RepoEntity> =
        throw UnsupportedOperationException("Paging is not exercised through FakeRepoDao")

    override suspend fun newestFetchedAt(needle: String): Long? =
        rows.value.values.filter { it.matches(needle) }.maxOfOrNull { it.fetchedAt }

    override suspend fun upsertAll(repos: List<RepoEntity>) {
        rows.value = rows.value + repos.associateBy { it.id }
    }

    override suspend fun deleteMatching(needle: String) {
        rows.value = rows.value.filterValues { !it.matches(needle) }
    }

    override suspend fun clear() {
        rows.value = emptyMap()
    }

    override suspend fun deleteOlderThan(cutoff: Long) {
        rows.value = rows.value.filterValues { it.fetchedAt >= cutoff }
    }

    /** The same matching rule as the SQL `LIKE`, so the fake does not flatter the code. */
    private fun RepoEntity.matches(needle: String): Boolean =
        needle.isEmpty() ||
            name.contains(needle, ignoreCase = true) ||
            language?.contains(needle, ignoreCase = true) == true
}
