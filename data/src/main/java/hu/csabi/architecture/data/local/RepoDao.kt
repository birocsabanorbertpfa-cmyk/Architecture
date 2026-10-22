package hu.csabi.architecture.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Lesson 08 — the DAO is the only thing in the app that speaks SQL.
 *
 * Two kinds of read, and the difference is the whole lesson:
 *  - `Flow<List<RepoEntity>>` is **observable**: Room re-runs the query and re-emits whenever
 *    any row the query touches changes. That is what makes the database a single source of
 *    truth — a write anywhere pushes to every collector, with no manual invalidation.
 *  - `suspend fun` is a one-shot read for a decision (how fresh is the cache?), where
 *    observing would be pointless.
 *
 * Every query is verified at **compile time**: a typo in a column name or a mismatch with the
 * entity fails the build instead of crashing on a user's device.
 */
@Dao
interface RepoDao {

    /**
     * `LIKE` with a bound parameter, not string concatenation: Room binds it, so there is no
     * injection surface and SQLite can reuse the prepared statement.
     */
    @Query(
        """
        SELECT * FROM repos
        WHERE name LIKE '%' || :needle || '%' OR language LIKE '%' || :needle || '%'
        ORDER BY stars DESC
        """,
    )
    fun observeMatching(needle: String): Flow<List<RepoEntity>>

    @Query("SELECT * FROM repos ORDER BY stars DESC")
    fun observeAll(): Flow<List<RepoEntity>>

    /**
     * Lesson 09 — Room generates the `PagingSource` from this query.
     *
     * Paging therefore reads **only** from the database; the network's job is to fill the
     * table, which is the same single-source-of-truth rule as lesson 08. Room also
     * invalidates this source automatically when a row changes, so an appended page shows
     * up without anyone telling the list to refresh.
     *
     * Ordering has to be stable, or pages overlap and items jump: `stars DESC, id` adds the
     * primary key as a tiebreaker so two repos with equal stars always come out in the same
     * order. The remote call asks for `sort=stars&order=desc`, so this matches the server.
     */
    @Query(
        """
        SELECT * FROM repos
        WHERE name LIKE '%' || :needle || '%' OR language LIKE '%' || :needle || '%'
        ORDER BY stars DESC, id ASC
        """,
    )
    fun pagingSource(needle: String): PagingSource<Int, RepoEntity>

    @Query(
        """
        DELETE FROM repos
        WHERE name LIKE '%' || :needle || '%' OR language LIKE '%' || :needle || '%'
        """,
    )
    suspend fun deleteMatching(needle: String)

    /** Used to decide whether the cached rows are still worth trusting. */
    @Query(
        """
        SELECT MAX(fetched_at) FROM repos
        WHERE name LIKE '%' || :needle || '%' OR language LIKE '%' || :needle || '%'
        """,
    )
    suspend fun newestFetchedAt(needle: String): Long?

    /**
     * `@Upsert` rather than `@Insert(onConflict = REPLACE)`.
     *
     * REPLACE is a delete followed by an insert: it fires foreign-key cascades and makes
     * every observing query re-emit even when nothing really changed. Upsert updates in
     * place, so the write is both cheaper and quieter.
     */
    @Upsert
    suspend fun upsertAll(repos: List<RepoEntity>)

    @Query("DELETE FROM repos")
    suspend fun clear()

    /** Eviction, so the table cannot grow forever. */
    @Query("DELETE FROM repos WHERE fetched_at < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
