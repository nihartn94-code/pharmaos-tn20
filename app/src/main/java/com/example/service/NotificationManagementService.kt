package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.PharmaApplication
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.DataStoreManager
import com.example.data.local.entity.NotificationEntity
import com.example.domain.model.InventoryStockItem
import com.example.domain.model.NotificationType
import com.example.domain.model.StockLevelStatus
import com.example.domain.model.StorageCondition
import com.example.domain.repository.PharmaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Model representing a low-stock inventory alert detected in the database.
 */
data class LowStockAlertItem(
    val id: Long,
    val productId: Long,
    val productName: String,
    val brand: String,
    val batchNumber: String,
    val remainingQuantity: Int,
    val userThreshold: Int,
    val isOutOfStock: Boolean,
    val isColdChain: Boolean,
    val alertTimestamp: Long
)

/**
 * State representing the active status of the Notification Management Service.
 */
data class NotificationServiceState(
    val isMonitoring: Boolean = false,
    val userThreshold: Int = DataStoreManager.DEFAULT_DEPLETION_THRESHOLD,
    val notificationsEnabled: Boolean = true,
    val monitoredItemsCount: Int = 0,
    val lowStockCount: Int = 0,
    val lowStockItems: List<LowStockAlertItem> = emptyList(),
    val totalNotificationsSent: Int = 0,
    val lastEvaluationTimestamp: Long = 0L
)

/**
 * Notification Management Service in Kotlin that continuously monitors
 * inventory levels in the Room database and triggers local push notifications
 * when a stock level falls below a user-defined threshold.
 */
class NotificationManagementService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitoringJob: Job? = null

    private val _serviceState = MutableStateFlow(NotificationServiceState())
    val serviceState: StateFlow<NotificationServiceState> = _serviceState.asStateFlow()

    // Cache to prevent duplicate notification spam for unchanged stock quantities
    private val lastAlertedQuantities = ConcurrentHashMap<Long, Int>()
    private val lastAlertTimestamps = ConcurrentHashMap<Long, Long>()

    // Re-alert after 3 hours if still below threshold, or immediately if stock drops further
    private val alertCooldownMillis = 3L * 60L * 60L * 1000L

    inner class LocalBinder : Binder() {
        fun getService(): NotificationManagementService = this@NotificationManagementService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_MONITORING -> {
                stopMonitoring()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CHECK_NOW -> {
                serviceScope.launch {
                    evaluateInventoryStockLevels(force = true)
                }
            }
            ACTION_SET_THRESHOLD -> {
                val newThreshold = intent.getIntExtra(EXTRA_THRESHOLD, DataStoreManager.DEFAULT_DEPLETION_THRESHOLD)
                setUserThreshold(newThreshold)
            }
            ACTION_START_MONITORING, null -> {
                startMonitoring()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring()
        serviceScope.cancel()
    }

    /**
     * Starts active observation of the Room database inventory flow and user threshold settings.
     */
    fun startMonitoring() {
        if (monitoringJob?.isActive == true) return

        val app = applicationContext as? PharmaApplication
        val repository = app?.repository
        val dataStoreManager = app?.dataStoreManager

        if (repository == null || dataStoreManager == null) {
            return
        }

        monitoringJob = serviceScope.launch {
            _serviceState.update { it.copy(isMonitoring = true) }

            combine(
                repository.getRealTimeInventoryFlow(),
                dataStoreManager.criticalDepletionThresholdFlow,
                dataStoreManager.autoDepletionNotificationsEnabledFlow
            ) { items, threshold, notificationsEnabled ->
                Triple(items, threshold, notificationsEnabled)
            }.collect { (items, threshold, notificationsEnabled) ->
                evaluateStockList(items, threshold, notificationsEnabled)
            }
        }
    }

    /**
     * Stops database observation.
     */
    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        _serviceState.update { it.copy(isMonitoring = false) }
    }

    /**
     * Updates the user-defined threshold and persists it to DataStore, immediately re-evaluating stock levels.
     */
    fun setUserThreshold(newThreshold: Int) {
        val app = applicationContext as? PharmaApplication ?: return
        serviceScope.launch {
            app.dataStoreManager.setCriticalDepletionThreshold(newThreshold)
            evaluateInventoryStockLevels(force = true)
        }
    }

    /**
     * Triggers an immediate one-off audit of all database inventory levels against the user threshold.
     */
    suspend fun evaluateInventoryStockLevels(force: Boolean = false): List<LowStockAlertItem> {
        val app = applicationContext as? PharmaApplication ?: return emptyList()
        val repository = app.repository
        val dataStoreManager = app.dataStoreManager

        val items = repository.getRealTimeInventoryFlow().first()
        val threshold = dataStoreManager.criticalDepletionThresholdFlow.first()
        val notificationsEnabled = dataStoreManager.autoDepletionNotificationsEnabledFlow.first()

        if (force) {
            lastAlertTimestamps.clear()
        }

        return evaluateStockList(items, threshold, notificationsEnabled)
    }

    /**
     * Core evaluation logic: inspects inventory items from the database against the user threshold
     * and triggers local push notifications and Room database notification records.
     */
    private suspend fun evaluateStockList(
        items: List<InventoryStockItem>,
        threshold: Int,
        notificationsEnabled: Boolean
    ): List<LowStockAlertItem> {
        val now = System.currentTimeMillis()
        val app = applicationContext as? PharmaApplication
        val db = app?.database
        val currentUser = app?.repository?.getApiUsersMe()
        val userId = currentUser?.id ?: 1L

        val lowStockList = mutableListOf<LowStockAlertItem>()
        var notificationsSentCount = 0

        for (item in items) {
            val isOutOfStock = item.remainingQuantity <= 0 || item.stockStatus == StockLevelStatus.OUT_OF_STOCK
            val isBelowThreshold = item.remainingQuantity <= threshold

            if (isBelowThreshold || isOutOfStock) {
                val isColdChain = item.storageCondition == StorageCondition.REFRIGERATED_2_8 ||
                        item.storageCondition == StorageCondition.FROZEN

                val alertItem = LowStockAlertItem(
                    id = item.id,
                    productId = item.productId,
                    productName = item.productName,
                    brand = item.brand,
                    batchNumber = item.batchNumber,
                    remainingQuantity = item.remainingQuantity,
                    userThreshold = threshold,
                    isOutOfStock = isOutOfStock,
                    isColdChain = isColdChain,
                    alertTimestamp = now
                )
                lowStockList.add(alertItem)

                if (notificationsEnabled) {
                    val lastQty = lastAlertedQuantities[item.id]
                    val lastTime = lastAlertTimestamps[item.id] ?: 0L

                    val shouldNotify = when {
                        lastQty == null -> true // Never notified
                        item.remainingQuantity < lastQty -> true // Quantity dropped lower
                        now - lastTime > alertCooldownMillis -> true // Cooldown expired
                        else -> false
                    }

                    if (shouldNotify) {
                        triggerLocalPushNotification(alertItem)

                        // Sync to Room Notifications table
                        if (db != null) {
                            try {
                                val title = if (isOutOfStock) {
                                    "🚨 Stockout Alert: ${item.productName}"
                                } else {
                                    "⚠️ Low Stock Alert: ${item.productName}"
                                }
                                val message = if (isOutOfStock) {
                                    "${item.productName} (${item.brand}) is completely depleted (0 units). Batch: ${item.batchNumber}. Restock immediately."
                                } else {
                                    "${item.productName} (${item.brand}) has fallen below your threshold of $threshold units (Current: ${item.remainingQuantity} units). Batch: ${item.batchNumber}."
                                }

                                db.notificationDao().insertNotification(
                                    NotificationEntity(
                                        recipientUserId = userId,
                                        type = NotificationType.SHORTAGE_RISK.name,
                                        title = title,
                                        message = message,
                                        referenceId = item.productId.toString(),
                                        isRead = false,
                                        createdAtEpochMillis = now
                                    )
                                )
                            } catch (_: Exception) {
                                // DB exception fallback
                            }
                        }

                        lastAlertedQuantities[item.id] = item.remainingQuantity
                        lastAlertTimestamps[item.id] = now
                        notificationsSentCount++
                    }
                }
            }
        }

        _serviceState.update { current ->
            current.copy(
                userThreshold = threshold,
                notificationsEnabled = notificationsEnabled,
                monitoredItemsCount = items.size,
                lowStockCount = lowStockList.size,
                lowStockItems = lowStockList,
                totalNotificationsSent = current.totalNotificationsSent + notificationsSentCount,
                lastEvaluationTimestamp = now
            )
        }

        return lowStockList
    }

    /**
     * Builds and dispatches a high-priority local push notification with custom actions and intent deep-linking.
     */
    private fun triggerLocalPushNotification(alert: LowStockAlertItem) {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "inventory_dashboard")
                putExtra("product_id", alert.productId)
                putExtra("batch_number", alert.batchNumber)
            }

            val pendingIntent = PendingIntent.getActivity(
                this,
                alert.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = if (alert.isOutOfStock) {
                "🚨 Critical Stockout: ${alert.productName}"
            } else {
                "⚠️ Low Stock Alert: ${alert.productName}"
            }

            val coldChainLabel = if (alert.isColdChain) " [Cold Chain]" else ""
            val body = if (alert.isOutOfStock) {
                "${alert.productName} (${alert.brand}) is completely OUT OF STOCK (0 units left)$coldChainLabel. Batch: ${alert.batchNumber}. Restock immediately."
            } else {
                "${alert.productName} (${alert.brand}) fell below your threshold of ${alert.userThreshold} units. Current stock: ${alert.remainingQuantity} units$coldChainLabel. Batch: ${alert.batchNumber}."
            }

            // Quick restock action intent
            val restockIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "catalogue")
                putExtra("product_id", alert.productId)
            }
            val restockPendingIntent = PendingIntent.getActivity(
                this,
                (alert.id.toInt() + 1000),
                restockIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(this, CHANNEL_INVENTORY_ALERTS)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle(title)
                        .bigText(body)
                        .setSummaryText("Threshold: ${alert.userThreshold} units")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(pendingIntent)
                .addAction(
                    android.R.drawable.ic_input_add,
                    "Restock Now",
                    restockPendingIntent
                )
                .setAutoCancel(true)
                .setVibrate(longArrayOf(0, 300, 200, 300))
                .setGroup(GROUP_INVENTORY_ALERTS)

            val notificationId = (alert.productId.hashCode() + alert.batchNumber.hashCode()) and 0x7FFFFFFF
            notificationManager.notify(notificationId, builder.build())
        } catch (_: Exception) {
            // Permission or notification manager error graceful catch
        }
    }

    /**
     * Creates dedicated high-importance notification channel for inventory alerts.
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_INVENTORY_ALERTS,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_INVENTORY_ALERTS = "pharmaos_inventory_monitor"
        private const val CHANNEL_NAME = "Inventory Stock Level Alerts"
        private const val CHANNEL_DESC = "Push notifications triggered when inventory levels fall below user-defined thresholds"
        private const val GROUP_INVENTORY_ALERTS = "com.example.inventory.ALERTS_GROUP"

        const val ACTION_START_MONITORING = "com.example.service.notification.START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.example.service.notification.STOP_MONITORING"
        const val ACTION_CHECK_NOW = "com.example.service.notification.CHECK_NOW"
        const val ACTION_SET_THRESHOLD = "com.example.service.notification.SET_THRESHOLD"
        const val EXTRA_THRESHOLD = "extra_threshold"

        /**
         * Starts the notification management service to begin database inventory monitoring.
         */
        fun start(context: Context) {
            try {
                val intent = Intent(context, NotificationManagementService::class.java).apply {
                    action = ACTION_START_MONITORING
                }
                context.startService(intent)
            } catch (_: Exception) {
                // Background start restriction handling
            }
        }

        /**
         * Stops the notification management service.
         */
        fun stop(context: Context) {
            try {
                val intent = Intent(context, NotificationManagementService::class.java).apply {
                    action = ACTION_STOP_MONITORING
                }
                context.startService(intent)
            } catch (_: Exception) {
                // Graceful fallback
            }
        }

        /**
         * Requests an immediate stock level check across the Room database.
         */
        fun checkNow(context: Context) {
            try {
                val intent = Intent(context, NotificationManagementService::class.java).apply {
                    action = ACTION_CHECK_NOW
                }
                context.startService(intent)
            } catch (_: Exception) {
                // Graceful fallback
            }
        }

        /**
         * Updates the user-defined threshold across the service.
         */
        fun setThreshold(context: Context, threshold: Int) {
            try {
                val intent = Intent(context, NotificationManagementService::class.java).apply {
                    action = ACTION_SET_THRESHOLD
                    putExtra(EXTRA_THRESHOLD, threshold)
                }
                context.startService(intent)
            } catch (_: Exception) {
                // Graceful fallback
            }
        }
    }
}
