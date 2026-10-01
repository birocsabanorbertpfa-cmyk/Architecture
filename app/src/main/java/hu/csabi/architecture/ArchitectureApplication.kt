package hu.csabi.architecture

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Lesson 06 — the entry point of the dependency graph.
 *
 * `@HiltAndroidApp` generates the `SingletonComponent` and attaches it to this Application
 * instance. Everything scoped `@Singleton` lives exactly as long as this object, which is
 * the honest meaning of "application-wide singleton" — not a Kotlin `object` that outlives
 * any sensible lifecycle and cannot be replaced in a test.
 */
@HiltAndroidApp
class ArchitectureApplication : Application()
