package hu.csabi.architecture.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import hu.csabi.architecture.data.fake.FakeRemoteDataSource
import hu.csabi.architecture.data.remote.RepoRemoteDataSource
import hu.csabi.architecture.data.repository.DefaultRepoRepository
import hu.csabi.architecture.domain.repository.RepoRepository
import javax.inject.Singleton

/**
 * Lesson 11 — replacing a module for the whole test run.
 *
 * `@TestInstallIn(replaces = [DataModule::class])` swaps the production bindings out at
 * compile time: every `@HiltAndroidTest` in this source set gets the fake network, and no
 * test has to know it happened. Compared with `@UninstallModules` plus a `@BindValue` per
 * test class, this keeps the arrangement in one file.
 *
 * The fake is configured with **no latency and no failures**, which is the real lesson here:
 * a UI test that waits on a random delay or a 20% failure rate is a flaky test. Determinism
 * is a property of the fake, not something the test can assert its way out of.
 *
 * Note what is *not* replaced: Room. Instrumented tests run against real SQLite on the
 * device, which is exactly where it should be exercised.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataModule::class])
interface TestDataModule {

    @Binds
    @Singleton
    fun bindRepoRepository(impl: DefaultRepoRepository): RepoRepository

    companion object {
        @Provides
        @Singleton
        fun remoteDataSource(): RepoRemoteDataSource =
            FakeRemoteDataSource(failureRate = 0f, latencyMillis = 0)
    }
}
