package hu.csabi.architecture

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import hu.csabi.architecture.domain.sync.RepoSyncScheduler
import javax.inject.Inject

/**
 * Lesson 06 — the entry point of the dependency graph.
 *
 * `@HiltAndroidApp` generates the `SingletonComponent` and attaches it to this Application
 * instance. Everything scoped `@Singleton` lives exactly as long as this object, which is
 * the honest meaning of "application-wide singleton" — not a Kotlin `object` that outlives
 * any sensible lifecycle and cannot be replaced in a test.
 *
 * Lesson 13 added the WorkManager wiring, and both halves are required:
 *
 *  1. `Configuration.Provider` hands WorkManager the `HiltWorkerFactory`. Without it,
 *     `@HiltWorker` workers fail at runtime with "could not instantiate worker" — WorkManager
 *     falls back to reflection and cannot supply the injected dependencies.
 *  2. The default initializer has to be **removed from the manifest**, otherwise WorkManager
 *     initialises itself before this configuration exists and ignores it.
 */
@HiltAndroidApp
class ArchitectureApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var syncScheduler: RepoSyncScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Safe on every cold start thanks to ExistingPeriodicWorkPolicy.KEEP.
        syncScheduler.ensurePeriodicSync()
    }
}
