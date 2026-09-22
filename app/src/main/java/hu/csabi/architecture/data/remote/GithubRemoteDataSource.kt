package hu.csabi.architecture.data.remote

import hu.csabi.architecture.core.coroutines.AppDispatchers
import hu.csabi.architecture.core.coroutines.DefaultAppDispatchers
import hu.csabi.architecture.core.model.Repo
import hu.csabi.architecture.core.model.SearchQuery
import hu.csabi.architecture.core.result.AppResult
import hu.csabi.architecture.core.result.map
import hu.csabi.architecture.data.remote.dto.toDomain
import kotlinx.coroutines.withContext

/**
 * Lesson 04 — the seam between "the network" and "the app".
 *
 * Above this class nothing knows Retrofit exists: the signature speaks in domain types
 * ([Repo], [SearchQuery]) and [AppResult]. That is what will let lesson 08 put a database
 * in front of it and lesson 10 swap in a fake, without either touching the ViewModel.
 */
class GithubRemoteDataSource(
    private val api: GithubApi = NetworkFactory.githubApi,
    private val dispatchers: AppDispatchers = DefaultAppDispatchers,
) {

    suspend fun searchRepositories(query: SearchQuery, page: Int = 1): AppResult<List<Repo>> =
        // Retrofit is already main-safe; the explicit switch is for the DTO -> domain
        // mapping, which is CPU work and has no business running on the main thread.
        withContext(dispatchers.io) {
            safeApiCall { api.searchRepositories(query = query.raw, page = page) }
                .map { response -> response.toDomain() }
        }

    suspend fun repoDetails(owner: String, name: String): AppResult<Repo> =
        withContext(dispatchers.io) {
            safeApiCall { api.repoDetails(owner, name) }.map { it.toDomain() }
        }
}
