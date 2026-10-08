package hu.csabi.architecture.domain.repository

import hu.csabi.architecture.core.result.AppResult
import androidx.paging.PagingData
import hu.csabi.architecture.domain.model.Repo
import hu.csabi.architecture.domain.model.SearchQuery
import hu.csabi.architecture.domain.model.Username
import kotlinx.coroutines.flow.Flow

/**
 * Lesson 05 — the contract lives in the domain, the implementation lives in data.
 *
 * This is dependency inversion: `data` depends on `domain`, never the other way round. The
 * domain layer can be compiled, read and tested without knowing that Retrofit, Room or
 * GitHub exist. Notice what the signatures do not mention — no DTO, no entity, no HTTP
 * status, no threading.
 *
 * Lesson 08 split reading from refreshing, which is the shape **single source of truth**
 * demands: [observeSearch] always answers from local storage, and [refresh] is the only
 * thing that talks to the network — it writes what it fetched and then says nothing more.
 * The UI therefore renders exactly one thing, the database, whether the data arrived a
 * second ago or last week.
 */
interface RepoRepository {

    /**
     * The stream the UI renders. It never fails: a network problem is [refresh]'s business,
     * and whatever was stored earlier stays visible regardless.
     */
    fun observeSearch(query: SearchQuery): Flow<List<Repo>>

    /**
     * Fetch and store. Returns whether the *fetch* succeeded — not the data, because the
     * data is already flowing through [observeSearch].
     *
     * @param force skips the freshness check and always hits the network, for pull-to-refresh.
     */
    suspend fun refresh(query: SearchQuery, force: Boolean = false): AppResult<Unit>

    /**
     * One-shot convenience for callers that are not observing. Still reads its result from
     * storage after refreshing, so it cannot disagree with [observeSearch].
     */
    suspend fun search(query: SearchQuery, page: Int = 1): AppResult<List<Repo>>

    /**
     * Lesson 09 — the paged stream.
     *
     * `PagingData` is an androidx type in a domain interface, which is a compromise worth
     * naming: wrapping it in a hand-rolled abstraction would mean reimplementing paging's
     * diffing and load states for no benefit. The rest of the layer stays clean, and this
     * one leak is documented rather than hidden.
     */
    fun pagedSearch(query: SearchQuery): Flow<PagingData<Repo>>

    suspend fun details(owner: Username, name: String): AppResult<Repo>

    /** Everything stored, newest-starred first. */
    fun observeCached(): Flow<List<Repo>>

    suspend fun clearCache()
}
