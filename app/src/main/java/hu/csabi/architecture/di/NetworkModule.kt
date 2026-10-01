package hu.csabi.architecture.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import hu.csabi.architecture.BuildConfig
import hu.csabi.architecture.data.remote.GithubApi
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Lesson 06 — the same network graph as lesson 04's `NetworkFactory`, declared instead of
 * constructed.
 *
 * Compare the two: the dependencies are identical, but here nothing calls a constructor by
 * hand. Each `@Provides` function states what it needs in its parameters, and Dagger works
 * out the order. `@Singleton` replaces `by lazy`, with a real lifetime attached to it — the
 * `SingletonComponent`, which lives as long as the application object.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://api.github.com/"

    @Provides
    @Singleton
    fun json(): Json = Json {
        // Without this, any field the backend adds later crashes the app.
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    /**
     * Multibinding: instead of one interceptor list assembled in one place, each
     * interceptor contributes itself with `@IntoSet`. Dagger collects them into a
     * `Set<Interceptor>`, so a new interceptor is a new function — no existing code edited.
     *
     * This is how a feature module adds behaviour to the shared client without the shared
     * client knowing the feature exists.
     */
    @Provides
    @IntoSet
    @Singleton
    fun githubHeaderInterceptor(): Interceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .apply {
                if (BuildConfig.GITHUB_TOKEN.isNotEmpty()) {
                    header("Authorization", "Bearer ${BuildConfig.GITHUB_TOKEN}")
                }
            }
            .build()
        chain.proceed(request)
    }

    @Provides
    @IntoSet
    @Singleton
    fun loggingInterceptor(): Interceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    /**
     * The injected `Set<Interceptor>` is unordered, which matters: interceptors that must
     * run before others cannot rely on multibinding alone. Here both are independent —
     * the header one only writes headers, the logger only reads them — so set semantics
     * are safe. When order matters, inject the pieces explicitly instead.
     */
    @Provides
    @Singleton
    fun okHttpClient(interceptors: Set<@JvmSuppressWildcards Interceptor>): OkHttpClient =
        OkHttpClient.Builder()
            .apply { interceptors.forEach(::addInterceptor) }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    @Provides
    @Singleton
    fun retrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun githubApi(retrofit: Retrofit): GithubApi = retrofit.create(GithubApi::class.java)
}
