package hu.csabi.architecture.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import hu.csabi.architecture.core.coroutines.AppDispatchers
import hu.csabi.architecture.data.fake.FakeRemoteDataSource
import hu.csabi.architecture.data.remote.GithubRemoteDataSource
import hu.csabi.architecture.data.remote.RepoRemoteDataSource
import hu.csabi.architecture.data.repository.DefaultRepoRepository
import hu.csabi.architecture.domain.repository.RepoRepository
import hu.csabi.architecture.domain.usecase.SearchRepositoriesUseCase
import javax.inject.Singleton

/**
 * Lesson 06 — binding interfaces to implementations.
 *
 * `@Binds` is an abstract function: it tells Dagger "when something asks for the interface,
 * use this implementation". It generates no code of its own and allocates nothing, so it is
 * strictly better than an `@Provides` function that just returns its argument.
 *
 * Because `@Binds` functions are abstract, the module has to be an `abstract class` or
 * `interface` — an `interface` is the idiomatic choice when every function is a binding.
 */
@Module
@InstallIn(SingletonComponent::class)
interface DataModule {

    @Binds
    @Singleton
    fun bindRemoteDataSource(impl: GithubRemoteDataSource): RepoRemoteDataSource

    @Binds
    @Singleton
    fun bindRepoRepository(impl: DefaultRepoRepository): RepoRepository
}

/**
 * The offline graph, behind a qualifier.
 *
 * Both this and the binding above produce a `RepoRepository`. Without [OfflineRepos] Dagger
 * would refuse to build — duplicate bindings are an error, not a coin toss. With it, the
 * ViewModels choose which one they want by annotating the injected parameter.
 */
@Module
@InstallIn(SingletonComponent::class)
object OfflineDataModule {

    @Provides
    @Singleton
    @OfflineRepos
    fun offlineRepository(dispatchers: AppDispatchers): RepoRepository =
        DefaultRepoRepository(
            remote = FakeRemoteDataSource(failureRate = 0.2f),
            dispatchers = dispatchers,
        )

    /**
     * The use case itself has `@Inject` on its constructor, which binds the *unqualified*
     * variant. The offline one has to be provided explicitly, because a qualifier cannot be
     * applied to a constructor binding after the fact.
     */
    @Provides
    @OfflineRepos
    fun offlineSearchUseCase(
        @OfflineRepos repository: RepoRepository,
    ): SearchRepositoriesUseCase = SearchRepositoriesUseCase(repository)
}
