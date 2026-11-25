package hu.csabi.architecture.data.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import hu.csabi.architecture.domain.sync.RepoSyncScheduler
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Lesson 13 — enqueueing work, and the four settings that decide whether it behaves.
 */
class WorkManagerSyncScheduler @Inject constructor(
    private val workManager: WorkManager,
) : RepoSyncScheduler {

    /**
     * Constraints are a promise to the user's battery. `CONNECTED` means WorkManager does not
     * even start the worker without a network, so the worker never has to handle "no
     * connection" as its normal case — and the OS, not the app, does the waiting.
     */
    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * `ExistingPeriodicWorkPolicy.KEEP` is the important argument.
     *
     * This is called from `Application.onCreate`, so it runs on every cold start. With
     * `UPDATE` (or the old `REPLACE`) each launch would reset the period, and a user who
     * opens the app often would never reach the interval — the sync would simply never run.
     * `KEEP` leaves an existing schedule alone.
     *
     * 15 minutes is also not arbitrary: it is WorkManager's floor for periodic work. Asking
     * for five gets you fifteen, silently.
     */
    override fun ensurePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<RepoSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            RepoSyncWorker.UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * `setBackoffCriteria` is WorkManager's own retry policy, and it is separate from the
     * `retryWithBackoff` operator: that one retries *inside* one attempt, this one reschedules
     * the whole worker after `Result.retry()`. Both exist because they cover different
     * failures — a flaky request versus a device that is offline for an hour.
     *
     * `ExistingWorkPolicy.REPLACE` is right here and wrong above: a newer manual sync request
     * supersedes a pending one, because the user asked for the *current* query.
     */
    override fun syncNow(query: String) {
        val request = OneTimeWorkRequestBuilder<RepoSyncWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(RepoSyncWorker.KEY_QUERY to query))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(
            RepoSyncWorker.UNIQUE_ONE_SHOT_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /**
     * WorkManager already exposes its state as an observable, so the UI can reflect a
     * background job without the app tracking it separately. Converting the `LiveData`-era
     * API to a Flow is a one-liner now.
     */
    override val isSyncing: Flow<Boolean> =
        workManager.getWorkInfosForUniqueWorkFlow(RepoSyncWorker.UNIQUE_ONE_SHOT_NAME)
            .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }
}
