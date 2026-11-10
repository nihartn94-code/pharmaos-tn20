package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.PharmaApplication
import java.util.concurrent.TimeUnit

/**
 * Periodic WorkManager task that verifies critical medicine stock levels
 * and triggers automated notifications even when the app is backgrounded.
 */
class InventoryDepletionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as? PharmaApplication ?: return Result.success()
            val monitor = app.inventoryMonitorManager
            monitor.runManualDepletionCheck()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "PharmaOSInventoryDepletionMonitorWork"

        fun schedulePeriodic(context: Context, intervalHours: Long = 1L) {
            val workRequest = PeriodicWorkRequestBuilder<InventoryDepletionWorker>(
                repeatInterval = intervalHours,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
