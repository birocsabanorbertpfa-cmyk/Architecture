package hu.csabi.architecture.di

import hu.csabi.architecture.core.coroutines.AppDispatchers
import hu.csabi.architecture.core.coroutines.DefaultAppDispatchers
import hu.csabi.architecture.data.fake.FakeRemoteDataSource
import hu.csabi.architecture.data.remote.GithubRemoteDataSource
import hu.csabi.architecture.data.remote.NetworkFactory
import hu.csabi.architecture.data.repository.DefaultRepoRepository
import hu.csabi.architecture.domain.repository.RepoRepository
import hu.csabi.architecture.domain.usecase.SearchRepositoriesUseCase

/**
 * Lesson 05 — the composition root, written by hand on purpose.
 *
 * Every dependency is assembled in exactly one place. Nothing below constructs its own
 * collaborators: callers ask for what they need, and this object decides which
 * implementation that is. It is also the only file in the app that names both
 * `FakeRemoteDataSource` and `GithubRemoteDataSource` — that is the seam working.
 *
 * Writing it manually first makes lesson 06 obvious: Hilt does not introduce a new idea, it
 * generates this file and manages the lifetimes. The costs on display here are the real
 * ones — every new dependency has to be threaded through by hand, `object` means process
 * lifetime whether or not anything needs it, and there is no scoping beyond `by lazy`.
 */
object ServiceLocator {

    val dispatchers: AppDispatchers = DefaultAppDispatchers

    /** The production graph: real HTTP against api.github.com. */
    val repoRepository: RepoRepository by lazy {
        DefaultRepoRepository(
            remote = GithubRemoteDataSource(NetworkFactory.githubApi, dispatchers),
            dispatchers = dispatchers,
        )
    }

    /**
     * The same graph with the network swapped for the fake. Identical types above the
     * data source, which is exactly the point of the RepoRemoteDataSource port.
     */
    val offlineRepoRepository: RepoRepository by lazy {
        DefaultRepoRepository(
            remote = FakeRemoteDataSource(failureRate = 0.2f),
            dispatchers = dispatchers,
        )
    }

    /**
     * Use cases are stateless and cheap, so a new instance per caller is fine. Only things
     * owning state or expensive resources need to be singletons.
     */
    fun searchRepositories(): SearchRepositoriesUseCase =
        SearchRepositoriesUseCase(repoRepository)

    fun searchRepositoriesOffline(): SearchRepositoriesUseCase =
        SearchRepositoriesUseCase(offlineRepoRepository)
}
