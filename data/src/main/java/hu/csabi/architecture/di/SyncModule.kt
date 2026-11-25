package hu.csabi.architecture.di

import android.content.Context
import androidx.work.WorkManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import hu.csabi.architecture.core.connectivity.NetworkMonitor
import hu.csabi.architecture.data.connectivity.ConnectivityNetworkMonitor
import hu.csabi.architecture.data.sync.WorkManagerSyncScheduler
import hu.csabi.architecture.domain.sync.RepoSyncScheduler
import javax.inject.Singleton

/**
 * Lesson 13 — binding the two new ports.
 */
@Module
@InstallIn(SingletonComponent::class)
interface SyncModule {

    @Binds
    @Singleton
    fun bindNetworkMonitor(impl: ConnectivityNetworkMonitor): NetworkMonitor

    @Binds
    @Singleton
    fun bindSyncScheduler(impl: WorkManagerSyncScheduler): RepoSyncScheduler

    companion object {
        /**
         * `WorkManager.getInstance` rather than a constructor: WorkManager initialises itself
         * from the Application and there is exactly one instance. Providing it through Dagger
         * anyway keeps the scheduler constructor-injectable and testable — `work-testing`
         * substitutes its own instance here.
         */
        @Provides
        @Singleton
        fun workManager(@ApplicationContext context: Context): WorkManager =
            WorkManager.getInstance(context)
    }
}
