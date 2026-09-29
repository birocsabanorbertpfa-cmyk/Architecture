package hu.csabi.architecture.data.remote

import hu.csabi.architecture.core.coroutines.AppDispatchers
import hu.csabi.architecture.core.coroutines.DefaultAppDispatchers
import hu.csabi.architecture.core.result.AppResult
import hu.csabi.architecture.core.result.map
import hu.csabi.architecture.data.remote.dto.toDomain
import hu.csabi.architecture.domain.model.Repo
import hu.csabi.architecture.domain.model.SearchQuery
import hu.csabi.architecture.domain.model.Username
import kotlinx.coroutines.withContext

/**
 * Lesson 04 — the seam between "the network" and "the app".
 *
 * Above this class nothing knows Retrofit exists: the signature speaks in domain types
 * ([Repo], [SearchQuery]) and [AppResult]. Lesson 05 pulled that signature out into
 * [RepoRemoteDataSource], so the caller now depends on the interface and this class is
 * simply one implementation of it.
 */
class GithubRemoteDataSource(
    private val api: GithubApi = NetworkFactory.githubApi,
    private val dispatchers: AppDispatchers = DefaultAppDispatchers,
) : RepoRemoteDataSource {

    override suspend fun searchRepositories(
        query: SearchQuery,
        page: Int,
    ): AppResult<List<Repo>> =
        // Retrofit is already main-safe; the explicit switch is for the DTO -> domain
        // mapping, which is CPU work and has no business running on the main thread.
        withContext(dispatchers.io) {
            safeApiCall { api.searchRepositories(query = query.raw, page = page) }
                .map { response -> response.toDomain() }
        }

    override suspend fun repoDetails(owner: Username, name: String): AppResult<Repo> =
        withContext(dispatchers.io) {
            safeApiCall { api.repoDetails(owner.value, name) }.map { it.toDomain() }
        }
}
