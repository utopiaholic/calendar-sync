package dev.albert.calmerge.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.albert.calmerge.CalMergeApp
import java.util.concurrent.TimeUnit

/**
 * FR-9: periodic background sync, 30-minute default. Timing is best-effort —
 * WorkManager defers under Doze; the spec accepts this.
 */
class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as CalMergeApp
        // Per-account failures are captured in account status (FR-24); the worker
        // itself only fails on catastrophic errors.
        return try {
            app.syncCoordinator.syncAll()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

object SyncScheduler {
    private const val PERIODIC_WORK_NAME = "calmerge_periodic_sync"
    const val DEFAULT_INTERVAL_MINUTES = 30L

    fun ensurePeriodicSync(context: Context, intervalMinutes: Long = DEFAULT_INTERVAL_MINUTES) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(intervalMinutes, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancelPeriodicSync(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
    }
}
