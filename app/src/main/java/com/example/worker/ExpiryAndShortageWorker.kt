package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.PharmaApplication
import com.example.data.local.entity.NotificationEntity
import com.example.domain.model.NotificationType
import com.example.domain.model.ShortageRiskLevel
import com.example.util.Formatters
import com.example.util.NotificationHelper
import java.util.concurrent.TimeUnit

class ExpiryAndShortageWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as? PharmaApplication ?: return Result.success()
            val repo = app.repository
            val now = System.currentTimeMillis()
            val thirtyDays = 30L * 24 * 60 * 60 * 1000

            // 1. Check near-expiry batches
            val batches = repo.getApiBatches()
            val currentUser = repo.getApiUsersMe()
            if (currentUser != null) {
                for (b in batches) {
                    if (b.daysUntilExpiry in 0..30 && b.batch.remainingQuantity > 0) {
                        NotificationHelper.showSystemNotification(
                            applicationContext,
                            b.batch.id.toInt(),
                            "Batch Expiring Soon: ${b.productName}",
                            "Batch ${b.batch.batchNumber} expires in ${b.daysUntilExpiry} days (${b.batch.remainingQuantity} units remaining)."
                        )
                    }
                }

                // 2. Evaluate shortage risks
                val shortageResults = repo.evaluateAllShortageRisks()
                val highRisks = shortageResults.filter { it.riskLevel == ShortageRiskLevel.HIGH }
                for (risk in highRisks) {
                    NotificationHelper.showSystemNotification(
                        applicationContext,
                        (10000 + risk.productId).toInt(),
                        "Critical Supply Shortage: ${risk.productName}",
                        risk.triggeredReasons.firstOrNull() ?: "Inventory critically depleted across suppliers."
                    )
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "PharmaOSDailyExpiryAndShortageWork"

        fun scheduleDaily(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<ExpiryAndShortageWorker>(
                repeatInterval = 24,
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
