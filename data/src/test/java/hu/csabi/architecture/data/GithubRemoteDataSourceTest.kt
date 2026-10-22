package hu.csabi.architecture.data

import com.google.common.truth.Truth.assertThat
import hu.csabi.architecture.core.error.AppError
import hu.csabi.architecture.core.result.AppResult
import hu.csabi.architecture.core.result.errorOrNull
import hu.csabi.architecture.core.result.getOrNull
import hu.csabi.architecture.data.remote.GithubApi
import hu.csabi.architecture.data.remote.GithubRemoteDataSource
import hu.csabi.architecture.di.NetworkModule
import hu.csabi.architecture.domain.model.SearchQuery
import hu.csabi.architecture.testing.TestAppDispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Lesson 10 — testing the network layer against a real HTTP server.
 *
 * `MockWebServer` serves canned responses over an actual socket, so Retrofit, OkHttp and
 * kotlinx.serialization all run for real. That is the difference between this and mocking
 * `GithubApi`: a mocked API can never catch a wrong `@SerialName`, a missing
 * `ignoreUnknownKeys`, or an error mapping that reads the wrong header.
 *
 * Note that the `Json` instance comes from [NetworkModule] rather than being rebuilt here.
 * A test that configures its own parser tests its own configuration, not production's.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GithubRemoteDataSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var dataSource: GithubRemoteDataSource

    private val dispatcher = StandardTestDispatcher()
    private val query = SearchQuery("okhttp")

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val json = NetworkModule.json()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GithubApi::class.java)

        dataSource = GithubRemoteDataSource(api, TestAppDispatchers(dispatcher))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `parses a search response into domain models`() = runTest(dispatcher) {
        server.enqueue(jsonResponse(SEARCH_BODY))

        val repos = dataSource.searchRepositories(query).getOrNull()

        assertThat(repos).hasSize(1)
        val repo = repos!!.first()
        assertThat(repo.fullName).isEqualTo("square/okhttp")
        assertThat(repo.stars.count).isEqualTo(46_200)
        assertThat(repo.language).isEqualTo("Kotlin")
    }

    @Test
    fun `sends the query and paging parameters GitHub expects`() = runTest(dispatcher) {
        server.enqueue(jsonResponse(SEARCH_BODY))

        dataSource.searchRepositoriesPage(SearchQuery("compose language:Kotlin"), page = 3, perPage = 20)

        val request = server.takeRequest()
        val url = request.requestUrl!!
        assertThat(url.encodedPath).isEqualTo("/search/repositories")
        assertThat(url.queryParameter("q")).isEqualTo("compose language:Kotlin")
        assertThat(url.queryParameter("page")).isEqualTo("3")
        assertThat(url.queryParameter("per_page")).isEqualTo("20")
        // Asserting the sort parameter matters: the DAO's ORDER BY assumes it.
        assertThat(url.queryParameter("sort")).isEqualTo("stars")
    }

    @Test
    fun `unknown fields in the response are ignored instead of crashing`() = runTest(dispatcher) {
        // The scenario `ignoreUnknownKeys` exists for: the backend adds a field.
        server.enqueue(jsonResponse(SEARCH_BODY_WITH_NEW_FIELD))

        val result = dataSource.searchRepositories(query)

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
    }

    @Test
    fun `one unmappable item is dropped, the rest survive`() = runTest(dispatcher) {
        // id = 0 fails RepoId's require(), so that single record is skipped.
        server.enqueue(jsonResponse(SEARCH_BODY_WITH_BAD_ITEM))

        val repos = dataSource.searchRepositories(query).getOrNull()

        assertThat(repos!!.map { it.name }).containsExactly("okhttp")
    }

    @Test
    fun `403 with an exhausted quota becomes RateLimited, not a generic HTTP error`() =
        runTest(dispatcher) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(403)
                    .setHeader("X-RateLimit-Remaining", "0")
                    .setHeader("X-RateLimit-Reset", "1700000000")
                    .setBody("""{"message":"API rate limit exceeded"}"""),
            )

            val error = dataSource.searchRepositories(query).errorOrNull()

            assertThat(error).isInstanceOf(AppError.RateLimited::class.java)
            assertThat((error as AppError.RateLimited).resetAtEpochSeconds).isEqualTo(1_700_000_000)
        }

    @Test
    fun `403 with quota left stays a plain HTTP error`() = runTest(dispatcher) {
        // The distinction the header makes: forbidden is not the same as throttled.
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("X-RateLimit-Remaining", "42")
                .setBody("""{"message":"Forbidden"}"""),
        )

        val error = dataSource.searchRepositories(query).errorOrNull()

        assertThat(error).isInstanceOf(AppError.Http::class.java)
        assertThat((error as AppError.Http).code).isEqualTo(403)
    }

    @Test
    fun `429 is treated as rate limiting even without the header`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(429).setBody("{}"))

        assertThat(dataSource.searchRepositories(query).errorOrNull())
            .isInstanceOf(AppError.RateLimited::class.java)
    }

    @Test
    fun `404 maps to an HTTP error carrying the code`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"Not Found"}"""))

        val error = dataSource.repoDetails(
            hu.csabi.architecture.domain.model.Username("square"),
            "nope",
        ).errorOrNull()

        assertThat((error as AppError.Http).code).isEqualTo(404)
    }

    @Test
    fun `a malformed body becomes a Serialization error`() = runTest(dispatcher) {
        server.enqueue(jsonResponse("""{"total_count":"not a number"}"""))

        assertThat(dataSource.searchRepositories(query).errorOrNull())
            .isInstanceOf(AppError.Serialization::class.java)
    }

    @Test
    fun `an HTML error page does not surface as a mysterious crash`() = runTest(dispatcher) {
        // A proxy or captive portal answering with HTML is a real production case.
        server.enqueue(jsonResponse("<html><body>502 Bad Gateway</body></html>"))

        assertThat(dataSource.searchRepositories(query).errorOrNull())
            .isInstanceOf(AppError.Serialization::class.java)
    }

    @Test
    fun `a dropped connection becomes a Network error`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START),
        )

        assertThat(dataSource.searchRepositories(query).errorOrNull())
            .isInstanceOf(AppError.Network::class.java)
    }

    private fun jsonResponse(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private companion object {
        val SEARCH_BODY = """
            {
              "total_count": 1,
              "incomplete_results": false,
              "items": [
                {
                  "id": 5,
                  "name": "okhttp",
                  "owner": { "login": "square", "avatar_url": "https://example.com/a.png" },
                  "description": "HTTP client for JVM and Android",
                  "stargazers_count": 46200,
                  "language": "Kotlin",
                  "html_url": "https://github.com/square/okhttp"
                }
              ]
            }
        """.trimIndent()

        val SEARCH_BODY_WITH_NEW_FIELD = """
            {
              "total_count": 1,
              "incomplete_results": false,
              "a_field_added_next_year": { "nested": true },
              "items": [
                {
                  "id": 5,
                  "name": "okhttp",
                  "owner": { "login": "square" },
                  "stargazers_count": 46200,
                  "some_new_flag": true
                }
              ]
            }
        """.trimIndent()

        val SEARCH_BODY_WITH_BAD_ITEM = """
            {
              "total_count": 2,
              "items": [
                { "id": 0, "name": "invalid", "owner": { "login": "nobody" } },
                { "id": 5, "name": "okhttp", "owner": { "login": "square" } }
              ]
            }
        """.trimIndent()
    }
}
