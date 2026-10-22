package hu.csabi.architecture.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import hu.csabi.architecture.core.coroutines.AppDispatchers
import hu.csabi.architecture.core.coroutines.DefaultAppDispatchers
import hu.csabi.architecture.core.coroutines.DefaultDispatcher
import hu.csabi.architecture.core.coroutines.IoDispatcher
import hu.csabi.architecture.core.coroutines.MainDispatcher
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Lesson 06 — the two kinds of binding, in one module.
 *
 * `@Binds` is abstract, `@Provides` has a body, and a module cannot be both an `object` and
 * abstract. The idiomatic solution is an `abstract class` for the bindings plus a
 * `companion object` for the provisions — Dagger looks in both.
 *
 * Why `@Provides` at all here: `CoroutineDispatcher` is a library type, so there is no
 * constructor to annotate. `@Binds` handles the opposite case — our own implementation of
 * our own interface — and generates no code beyond a cast.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CoroutinesModule {

    @Binds
    @Singleton
    abstract fun bindAppDispatchers(impl: DefaultAppDispatchers): AppDispatchers

    companion object {

        @Provides
        @IoDispatcher
        fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

        @Provides
        @DefaultDispatcher
        fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

        @Provides
        @MainDispatcher
        fun mainDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate
    }
}
