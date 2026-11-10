package com.example.service

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.DataStoreManager
import com.example.data.local.entity.NotificationEntity
import com.example.domain.model.InventoryStockItem
import com.example.domain.model.NotificationType
import com.example.domain.model.StockLevelStatus
import com.example.domain.model.StorageCondition
import com.example.domain.repository.PharmaRepository
import com.example.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

data class InventoryMonitorState(
    val isMonitoringActive: Boolean = true,
    val criticalThreshold: Int = 10,
    val notificationsEnabled: Boolean = true,
    val lastScanTimestamp: Long = 0L,
    val activeDepletionAlertsCount: Int = 0,
    val criticalDepletedItems: List<InventoryStockItem> = emptyList(),
    val totalAlertsTriggeredCount: Int = 0
)

/**
 * Service manager that continuously monitors inventory levels from Room database flows
 * and triggers automated push notifications when critical medicines reach stock depletion thresholds.
 */
class InventoryMonitorManager(
    private val context: Context,
    private val repository: PharmaRepository,
    private val db: AppDatabase,
    private val dataStoreManager: DataStoreManager,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

    private val _monitorState = MutableStateFlow(InventoryMonitorState())
    val monitorState: StateFlow<InventoryMonitorState> = _monitorState.asStateFlow()

    private var monitoringJob: Job? = null

    // Cache to prevent duplicate notification spam for identical stock levels
    private val lastAlertedQuantities = ConcurrentHashMap<Long, Int>()
    private val lastAlertTimestamps = ConcurrentHashMap<Long, Long>()

    // Alert cooldown: re-notify after 3 hours if still critical, or immediately if stock drops further
    private val alertCooldownMillis = 3L * 60L * 60L * 1000L

    fun startMonitoring() {
        if (monitoringJob?.isActive == true) return

        monitoringJob = scope.launch {
            _monitorState.update { it.copy(isMonitoringActive = true) }

            combine(
                repository.getRealTimeInventoryFlow(),
                dataStoreManager.criticalDepletionThresholdFlow,
                dataStoreManager.autoDepletionNotificationsEnabledFlow
            ) { items, threshold, autoNotifyEnabled ->
                Triple(items, threshold, autoNotifyEnabled)
            }.collect { (items, threshold, autoNotifyEnabled) ->
                evaluateInventoryDepletion(items, threshold, autoNotifyEnabled)
            }
        }
    }

    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        _monitorState.update { it.copy(isMonitoringActive = false) }
    }

    /**
     * Evaluates current inventory stock items against critical depletion thresholds.
     */
    private suspend fun evaluateInventoryDepletion(
        items: List<InventoryStockItem>,
        threshold: Int,
        autoNotifyEnabled: Boolean
    ) {
        val now = System.currentTimeMillis()

        // Critical depletion candidate items:
        // Remaining quantity <= threshold (or out of stock)
        val depletedItems = items.filter { item ->
            item.remainingQuantity <= threshold || item.stockStatus == StockLevelStatus.OUT_OF_STOCK
        }

        var newAlertsTriggeredThisRun = 0

        if (autoNotifyEnabled) {
            val currentUser = repository.getApiUsersMe()
            val userId = currentUser?.id ?: 1L

            for (item in depletedItems) {
                val itemId = item.id
                val remaining = item.remainingQuantity
                val lastQuantity = lastAlertedQuantities[itemId]
                val lastTime = lastAlertTimestamps[itemId] ?: 0L

                val shouldAlert = when {
                    // 1. Never alerted before
                    lastQuantity == null -> true
                    // 2. Quantity dropped even further (e.g. from 8 to 2, or 2 to 0)
                    remaining < lastQuantity -> true
                    // 3. Cooldown expired and still critically depleted
                    now - lastTime > alertCooldownMillis -> true
                    else -> false
                }

                if (shouldAlert) {
                    val isOutOfStock = remaining <= 0
                    val isColdChain = item.storageCondition == StorageCondition.REFRIGERATED_2_8 ||
                            item.storageCondition == StorageCondition.FROZEN

                    val title = if (isOutOfStock) {
                        "🚨 Stockout Alert: ${item.productName}"
                    } else {
                        "⚠️ Critical Depletion: ${item.productName}"
                    }

                    val coldChainNotice = if (isColdChain) " [Cold Chain Critical]" else ""
                    val message = if (isOutOfStock) {
                        "${item.productName} (${item.brand}) is completely OUT OF STOCK (0 units left)$coldChainNotice. Batch: ${item.batchNumber}. Restock immediately."
                    } else {
                        "${item.productName} (${item.brand}) has reached critical depletion: only $remaining units remaining (Threshold: $threshold)$coldChainNotice. Batch: ${item.batchNumber}."
                    }

                    val notificationId = (item.productId.hashCode() + item.batchNumber.hashCode()) and 0x7FFFFFFF

                    // 1. Trigger automated System Push Notification
                    NotificationHelper.showCriticalStockNotification(
                        context = context,
                        notificationId = notificationId,
                        title = title,
                        message = message,
                        productId = item.productId
                    )

                    // 2. Record in Room Notification Database for in-app alerts history
                    try {
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
                        // DB insertion error graceful catch
                    }

                    lastAlertedQuantities[itemId] = remaining
                    lastAlertTimestamps[itemId] = now
                    newAlertsTriggeredThisRun++
                }
            }
        }

        _monitorState.update { current ->
            current.copy(
                criticalThreshold = threshold,
                notificationsEnabled = autoNotifyEnabled,
                lastScanTimestamp = now,
                activeDepletionAlertsCount = depletedItems.size,
                criticalDepletedItems = depletedItems,
                totalAlertsTriggeredCount = current.totalAlertsTriggeredCount + newAlertsTriggeredThisRun
            )
        }
    }

    /**
     * Executes an immediate manual stock scan across all inventory items
     * and triggers notifications for critical items.
     */
    suspend fun runManualDepletionCheck(): Int {
        val items = repository.getRealTimeInventoryFlow().first()
        val threshold = dataStoreManager.criticalDepletionThresholdFlow.first()
        val autoNotify = dataStoreManager.autoDepletionNotificationsEnabledFlow.first()

        // Clear cooldown to force fresh evaluation if requested manually
        lastAlertTimestamps.clear()
        evaluateInventoryDepletion(items, threshold, autoNotify)

        return items.count { it.remainingQuantity <= threshold || it.stockStatus == StockLevelStatus.OUT_OF_STOCK }
    }

    fun setCriticalThreshold(threshold: Int) {
        scope.launch {
            dataStoreManager.setCriticalDepletionThreshold(threshold)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        scope.launch {
            dataStoreManager.setAutoDepletionNotificationsEnabled(enabled)
        }
    }
}
