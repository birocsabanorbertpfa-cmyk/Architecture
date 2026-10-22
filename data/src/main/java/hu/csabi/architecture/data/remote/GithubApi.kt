package hu.csabi.architecture.data.remote

import hu.csabi.architecture.data.remote.dto.RepoDto
import hu.csabi.architecture.data.remote.dto.SearchResponseDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Lesson 04 — the API surface, declared rather than implemented.
 *
 * Retrofit generates the implementation from these annotations. The functions are `suspend`,
 * so Retrofit hands back the parsed body directly and throws on failure — there is no
 * `Call<T>` and no callback. Retrofit already moves the work off the calling thread, so
 * wrapping these in `withContext(IO)` is redundant.
 *
 * Returning DTOs (not domain types) is deliberate: this interface describes the wire, and
 * the translation happens one layer up.
 */
interface GithubApi {

    /**
     * https://docs.github.com/rest/search/search#search-repositories
     *
     * A `@Query` value is URL-encoded by Retrofit, which is what makes the lesson 01 query
     * DSL safe to pass through verbatim.
     */
    @GET("search/repositories")
    suspend fun searchRepositories(
        @Query("q") query: String,
        @Query("sort") sort: String = "stars",
        @Query("order") order: String = "desc",
        @Query("per_page") perPage: Int = 20,
        @Query("page") page: Int = 1,
    ): SearchResponseDto

    @GET("repos/{owner}/{name}")
    suspend fun repoDetails(
        @Path("owner") owner: String,
        @Path("name") name: String,
    ): RepoDto
}
