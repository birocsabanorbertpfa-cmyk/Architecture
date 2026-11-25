package hu.csabi.architecture.domain.sync

import kotlinx.coroutines.flow.Flow

/**
 * Lesson 13 — "sync happens" is a domain concern; "WorkManager does it" is not.
 *
 * The domain states the capability, the data layer owns the mechanism. That split is what
 * lets a caller ask for a sync without importing WorkManager, and it is why swapping the
 * implementation for a plain coroutine (or a test double) touches nothing above here.
 */
interface RepoSyncScheduler {

    /** Register the recurring background sync. Safe to call repeatedly. */
    fun ensurePeriodicSync()

    /** Ask for one sync as soon as the constraints allow it. */
    fun syncNow(query: String)

    /** Whether a sync is currently enqueued or running, for the UI to reflect. */
    val isSyncing: Flow<Boolean>
}
