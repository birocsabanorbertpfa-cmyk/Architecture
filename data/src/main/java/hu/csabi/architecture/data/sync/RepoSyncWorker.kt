package hu.csabi.architecture.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import hu.csabi.architecture.core.result.AppResult
import hu.csabi.architecture.domain.usecase.SearchRepositoriesUseCase

/**
 * Lesson 13 — work that outlives the UI.
 *
 * `CoroutineWorker`, not `Worker`: `doWork` is a `suspend` function, so the existing suspend
 * repository is called directly and cancellation works the way it does everywhere else in
 * the app. A plain `Worker` would mean blocking a WorkManager thread.
 *
 * `@HiltWorker` plus `@AssistedInject` is the awkward-looking part, and it is unavoidable:
 * WorkManager constructs workers itself and supplies the `Context` and `WorkerParameters`,
 * so those two come in as `@Assisted` while Dagger provides the rest. The generated factory
 * is what the `HiltWorkerFactory` in the Application looks up.
 *
 * The return value is an API, not a formality:
 *  - `success()` — done, do not run again
 *  - `retry()`   — transient failure; WorkManager re-enqueues with *its own* backoff
 *  - `failure()` — permanent; stop trying
 *
 * Returning `failure()` for a network blip means the sync silently never happens, and
 * returning `retry()` for a bad request means retrying forever. Mapping them from
 * `AppError.isRetryable` keeps that decision in one place.
 */
@HiltWorker
class RepoSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val searchRepositories: SearchRepositoriesUseCase,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val query = inputData.getString(KEY_QUERY) ?: DEFAULT_QUERY

        return when (val result = searchRepositories.refresh(query, force = true)) {
            is AppResult.Success -> Result.success()
            is AppResult.Failure -> if (result.error.isRetryable) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        const val KEY_QUERY = "query"
        const val DEFAULT_QUERY = "kotlin"
        const val UNIQUE_PERIODIC_NAME = "repo-sync-periodic"
        const val UNIQUE_ONE_SHOT_NAME = "repo-sync-now"
    }
}
