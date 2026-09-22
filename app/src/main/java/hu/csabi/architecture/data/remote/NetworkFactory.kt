package hu.csabi.architecture.data.remote

import hu.csabi.architecture.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Lesson 04 — manual wiring of the network stack.
 *
 * Every object here is expensive to build and meant to be a singleton: one `OkHttpClient`
 * owns the connection pool, the thread pool and the cache, so creating one per request is
 * the classic performance bug. Lesson 06 replaces this hand-written factory with Hilt
 * modules — which is exactly the same graph, just declared instead of constructed.
 */
object NetworkFactory {

    private const val BASE_URL = "https://api.github.com/"

    /**
     * `ignoreUnknownKeys` is the single most important setting: without it, any new field
     * added by the backend crashes the app. `explicitNulls = false` keeps nulls out of
     * request bodies rather than sending `"field": null`.
     */
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    /**
     * Interceptors run in the order they are added. Anything that modifies the request
     * (headers, auth) must come before the logger, otherwise the log shows a request that
     * was never actually sent.
     */
    private val headerInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .apply {
                // Unauthenticated calls are limited to 60 requests/hour. A token in
                // local.properties raises that to 5000 without being committed.
                if (BuildConfig.GITHUB_TOKEN.isNotEmpty()) {
                    header("Authorization", "Bearer ${BuildConfig.GITHUB_TOKEN}")
                }
            }
            .build()
        chain.proceed(request)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        // BODY logging prints the full payload — useful in debug, a privacy leak in release.
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(headerInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS) // upper bound for the whole call, retries included
            .retryOnConnectionFailure(true)
            .build()
    }

    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    val githubApi: GithubApi by lazy { retrofit.create(GithubApi::class.java) }
}
