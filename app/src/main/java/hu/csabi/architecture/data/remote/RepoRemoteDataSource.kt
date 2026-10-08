package hu.csabi.architecture.data.remote

import hu.csabi.architecture.core.result.AppResult
import hu.csabi.architecture.domain.model.Repo
import hu.csabi.architecture.domain.model.RepoPage
import hu.csabi.architecture.domain.model.SearchQuery
import hu.csabi.architecture.domain.model.Username

/**
 * Lesson 05 — the port the repository talks to.
 *
 * Lesson 04 had the ViewModel hold a concrete `GithubRemoteDataSource`. With an interface
 * here, the repository depends on a capability ("something that can search repos") rather
 * than on an implementation, so the fake and the real network client become interchangeable
 * — in the app, and in lesson 10's tests.
 */
interface RepoRemoteDataSource {

    suspend fun searchRepositories(query: SearchQuery, page: Int = 1): AppResult<List<Repo>>

    /**
     * Lesson 09 — the paged read, which also reports the total so the mediator can decide
     * when pagination has reached the end. Both implementations had to follow; that is the
     * cost of widening a port, and the reason to keep ports narrow.
     */
    suspend fun searchRepositoriesPage(
        query: SearchQuery,
        page: Int,
        perPage: Int,
    ): AppResult<RepoPage>

    suspend fun repoDetails(owner: Username, name: String): AppResult<Repo>
}
