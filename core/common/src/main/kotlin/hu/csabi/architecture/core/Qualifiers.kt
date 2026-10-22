package hu.csabi.architecture.di

import javax.inject.Qualifier

/**
 * Lesson 06 — qualifiers answer "which one?".
 *
 * Marks the fake-backed graph. Both it and the real one produce a `RepoRepository`, and
 * duplicate bindings of the same type are a build error — Dagger refuses to guess. The
 * qualifier turns the binding key into `(type, qualifier)`, so the Flow lab can ask for the
 * offline graph while the Network lab gets the real one.
 *
 * The dispatcher qualifiers live in `core.coroutines`, next to the abstraction they
 * describe, so `core` does not depend on this package.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OfflineRepos
