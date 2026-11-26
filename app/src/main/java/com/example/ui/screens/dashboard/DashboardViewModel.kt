package com.example.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.NotificationEntity
import com.example.data.local.entity.OrderEntity
import com.example.data.local.entity.ProductInventoryEntity
import com.example.domain.model.AuthUser
import com.example.domain.model.InventoryDashboardSummary
import com.example.domain.model.InventoryStockItem
import com.example.domain.model.NotificationType
import com.example.domain.model.OrderStatus
import com.example.domain.model.RecallSeverity
import com.example.domain.model.ShortageEvaluationResult
import com.example.domain.model.ShortageRiskLevel
import com.example.domain.repository.DashboardKpis
import com.example.domain.repository.PharmaRepository
import com.example.domain.repository.RecallWithAffectedStock
import com.example.ui.components.OrderTransactionUiModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TradeAlertSeverity(val label: String, val priority: Int) {
    CRITICAL("Critical", 0),
    WARNING("Warning", 1),
    INFO("Updates", 2)
}

enum class TradeAlertCategory(val label: String) {
    RECALL("Recall"),
    SHORTAGE("Shortage"),
    EXPIRY("Expiry"),
    INVENTORY("Stock"),
    ORDER("Order"),
    TRADE("Trade")
}

data class DashboardTradeAlert(
    val id: String,
    val notificationId: Long? = null,
    val title: String,
    val message: String,
    val category: TradeAlertCategory,
    val severity: TradeAlertSeverity,
    val timestampEpochMillis: Long,
    val isUnread: Boolean = true
)

data class PendingOrderPreview(
    val orderId: Long,
    val orderNumber: String,
    val status: OrderStatus,
    val totalAmount: Double,
    val expectedDeliveryEpochMillis: Long?,
    val createdAtEpochMillis: Long
)

data class PendingOrdersSummary(
    val totalPendingCount: Int = 0,
    val totalPendingValue: Double = 0.0,
    val placedCount: Int = 0,
    val processingCount: Int = 0,
    val dispatchedCount: Int = 0,
    val pendingOrders: List<PendingOrderPreview> = emptyList()
)

data class CatalogueSupplySummary(
    val activeProductsCount: Int = 0,
    val totalSupplierOffersCount: Int = 0,
    val inStockCatalogueCount: Int = 0
)

data class DashboardUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val currentUser: AuthUser? = null,
    val isRealTimeInventoryAuthorized: Boolean = false,
    val kpis: DashboardKpis? = null,
    val unreadNotificationsCount: Int = 0,
    val cartItemCount: Int = 0,
    val inventoryItems: List<ProductInventoryEntity> = emptyList(),
    val realTimeInventory: List<InventoryStockItem> = emptyList(),
    val inventorySummary: InventoryDashboardSummary = InventoryDashboardSummary(),
    val catalogueSupplySummary: CatalogueSupplySummary = CatalogueSupplySummary(),
    val activeTradeAlerts: List<DashboardTradeAlert> = emptyList(),
    val selectedAlertFilter: TradeAlertSeverity? = null,
    val pendingOrdersSummary: PendingOrdersSummary = PendingOrdersSummary(),
    val recentOrderTransactions: List<OrderTransactionUiModel> = emptyList(),
    val activeOfferBanners: List<com.example.domain.model.OfferBanner> = emptyList(),
    val publishedAnnouncements: List<com.example.domain.model.MedicineAnnouncement> = emptyList(),
    val stockistPurchasedInventoryCount: Int = 0,
    val shortageAlertsCount: Int = 0,
    val reorderSuccessMessage: String? = null,
    val errorMessage: String? = null
) {
    val filteredTradeAlerts: List<DashboardTradeAlert>
        get() = selectedAlertFilter?.let { filter ->
            activeTradeAlerts.filter { it.severity == filter }
        } ?: activeTradeAlerts

    val criticalAlertsCount: Int
        get() = activeTradeAlerts.count { it.severity == TradeAlertSeverity.CRITICAL }
}

class DashboardViewModel(
    private val repository: PharmaRepository,
    private val workspaceRepository: com.example.domain.repository.WorkspaceRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        observeCurrentUser()
        loadDashboardData()
        observeNotifications()
        observeBasket()
        observeInventory()
        observeRealTimeInventory()
        observeCatalogueSupply()
        observePendingOrders()
        observeTradeAlerts()
        observeStockistPromotionalFeed()
    }

    private fun observeStockistPromotionalFeed() {
        val wsRepo = workspaceRepository ?: return
        viewModelScope.launch {
            wsRepo.ensureWorkspaceSeededForLocalDev()
        }
        viewModelScope.launch {
            combine(
                wsRepo.getStockistActiveBannersFlow(),
                wsRepo.getStockistPublishedAnnouncementsFlow(),
                wsRepo.getStockistPurchasedInventoryFlow()
            ) { banners, announcements, purchased ->
                Triple(banners, announcements, purchased.size)
            }.collect { (banners, announcements, purchasedCount) ->
                _uiState.update {
                    it.copy(
                        activeOfferBanners = banners,
                        publishedAnnouncements = announcements,
                        stockistPurchasedInventoryCount = purchasedCount
                    )
                }
            }
        }
    }

    private fun observeCurrentUser() {
        viewModelScope.launch {
            repository.getApiUsersMeFlow().collect { user ->
                val authorized = user?.role?.canAccessRealTimeInventory == true
                _uiState.update { current ->
                    current.copy(
                        currentUser = user,
                        isRealTimeInventoryAuthorized = authorized,
                        inventoryItems = if (authorized) current.inventoryItems else emptyList(),
                        realTimeInventory = if (authorized) current.realTimeInventory else emptyList(),
                        inventorySummary = if (authorized) current.inventorySummary else InventoryDashboardSummary(),
                        shortageAlertsCount = if (authorized) current.shortageAlertsCount else 0
                    )
                }
            }
        }
    }

    fun loadDashboardData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val user = repository.getApiUsersMe()
                val authorized = user?.role?.canAccessRealTimeInventory == true
                val kpis = repository.getDashboardStats()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentUser = user,
                        isRealTimeInventoryAuthorized = authorized,
                        inventoryItems = if (authorized) it.inventoryItems else emptyList(),
                        realTimeInventory = if (authorized) it.realTimeInventory else emptyList(),
                        inventorySummary = if (authorized) it.inventorySummary else InventoryDashboardSummary(),
                        shortageAlertsCount = if (authorized) it.shortageAlertsCount else 0,
                        kpis = kpis,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to load dashboard"
                    )
                }
            }
        }
    }

    fun refreshDashboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            try {
                val user = repository.getApiUsersMe()
                val authorized = user?.role?.canAccessRealTimeInventory == true
                if (authorized) {
                    repository.refreshRealTimeInventory()
                }
                val kpis = repository.getDashboardStats()
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        currentUser = user,
                        isRealTimeInventoryAuthorized = authorized,
                        kpis = kpis
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        errorMessage = e.message ?: "Unable to refresh dashboard"
                    )
                }
            }
        }
    }

    private fun observeNotifications() {
        viewModelScope.launch {
            repository.getApiNotificationsUnreadCountFlow().collect { count ->
                _uiState.update { it.copy(unreadNotificationsCount = count) }
            }
        }
    }

    private fun observeBasket() {
        viewModelScope.launch {
            repository.getApiBasketFlow().collect { basket ->
                val count = basket.items.sumOf { it.quantity }
                _uiState.update { it.copy(cartItemCount = count) }
            }
        }
    }

    private fun observeInventory() {
        viewModelScope.launch {
            combine(
                repository.getApiUsersMeFlow(),
                repository.getProductInventoryFlow()
            ) { user, items ->
                val authorized = user?.role?.canAccessRealTimeInventory == true
                if (authorized) items else emptyList()
            }.collect { items ->
                val critical = items.count { it.currentStock <= it.reorderThreshold }
                _uiState.update { current ->
                    current.copy(
                        inventoryItems = items,
                        shortageAlertsCount = critical
                    )
                }
            }
        }
    }

    private fun observeRealTimeInventory() {
        viewModelScope.launch {
            combine(
                repository.getApiUsersMeFlow(),
                repository.getRealTimeInventoryFlow(),
                repository.getInventorySummaryFlow()
            ) { user, items, summary ->
                val authorized = user?.role?.canAccessRealTimeInventory == true
                if (authorized) items to summary else emptyList<InventoryStockItem>() to InventoryDashboardSummary()
            }.collect { (items, summary) ->
                _uiState.update { current ->
                    current.copy(
                        realTimeInventory = items,
                        inventorySummary = summary
                    )
                }
            }
        }
    }

    private fun observeCatalogueSupply() {
        viewModelScope.launch {
            repository.getApiProductsFlow().collect { products ->
                val activeProducts = products.filter { it.isActive }
                val totalOffers = activeProducts.sumOf { it.activeOfferCount }
                val inStockCount = activeProducts.count { it.activeOfferCount > 0 }
                _uiState.update {
                    it.copy(
                        catalogueSupplySummary = CatalogueSupplySummary(
                            activeProductsCount = activeProducts.size,
                            totalSupplierOffersCount = totalOffers,
                            inStockCatalogueCount = inStockCount
                        )
                    )
                }
            }
        }
    }

    private fun observePendingOrders() {
        viewModelScope.launch {
            combine(
                repository.getApiOrdersFlow(),
                repository.getOrderHistoryTransactionsFlow()
            ) { orders, transactions ->
                buildPendingOrdersSummary(orders) to transactions
            }.collect { (summary, transactions) ->
                _uiState.update {
                    it.copy(
                        pendingOrdersSummary = summary,
                        recentOrderTransactions = transactions
                    )
                }
            }
        }
    }

    private fun observeTradeAlerts() {
        viewModelScope.launch {
            combine(
                repository.getApiUsersMeFlow(),
                repository.getApiNotificationsFlow(),
                repository.getApiShortageAlertsFlow(),
                repository.getApiRecallsFlow(),
                repository.getRealTimeInventoryFlow()
            ) { user, notifications, shortages, recalls, realTimeItems ->
                val authorized = user?.role?.canAccessRealTimeInventory == true
                buildActiveTradeAlerts(
                    notifications = notifications,
                    shortages = shortages,
                    recalls = recalls,
                    authorizedInventoryItems = if (authorized) realTimeItems else emptyList()
                )
            }.collect { alerts ->
                _uiState.update { it.copy(activeTradeAlerts = alerts) }
            }
        }
    }

    private fun buildPendingOrdersSummary(orders: List<OrderEntity>): PendingOrdersSummary {
        val pendingStatuses = setOf(
            OrderStatus.PLACED,
            OrderStatus.ACCEPTED,
            OrderStatus.PACKED,
            OrderStatus.DISPATCHED,
            OrderStatus.PARTIALLY_FULFILLED
        )
        val parsed = orders.mapNotNull { entity ->
            val status = try {
                OrderStatus.valueOf(entity.status)
            } catch (_: Exception) {
                null
            }
            if (status != null && status in pendingStatuses) entity to status else null
        }.sortedByDescending { it.first.createdAtEpochMillis }

        val placedCount = parsed.count { it.second == OrderStatus.PLACED }
        val processingCount = parsed.count {
            it.second == OrderStatus.ACCEPTED ||
                it.second == OrderStatus.PACKED ||
                it.second == OrderStatus.PARTIALLY_FULFILLED
        }
        val dispatchedCount = parsed.count { it.second == OrderStatus.DISPATCHED }

        val previews = parsed.take(4).map { (entity, status) ->
            PendingOrderPreview(
                orderId = entity.id,
                orderNumber = entity.orderNumber,
                status = status,
                totalAmount = entity.totalAmount,
                expectedDeliveryEpochMillis = entity.expectedDeliveryEpochMillis,
                createdAtEpochMillis = entity.createdAtEpochMillis
            )
        }

        return PendingOrdersSummary(
            totalPendingCount = parsed.size,
            totalPendingValue = parsed.sumOf { it.first.totalAmount },
            placedCount = placedCount,
            processingCount = processingCount,
            dispatchedCount = dispatchedCount,
            pendingOrders = previews
        )
    }

    private fun buildActiveTradeAlerts(
        notifications: List<NotificationEntity>,
        shortages: List<ShortageEvaluationResult>,
        recalls: List<RecallWithAffectedStock>,
        authorizedInventoryItems: List<InventoryStockItem>
    ): List<DashboardTradeAlert> {
        val alerts = mutableListOf<DashboardTradeAlert>()

        // 1. Active product recalls
        recalls.filter { it.recall.isActive }.forEach { entry ->
            val severity = when {
                entry.recall.severity.equals(RecallSeverity.CRITICAL_CLASS_1.name, ignoreCase = true) ->
                    TradeAlertSeverity.CRITICAL
                entry.recall.severity.equals(RecallSeverity.MAJOR_CLASS_2.name, ignoreCase = true) ->
                    TradeAlertSeverity.WARNING
                else -> TradeAlertSeverity.INFO
            }
            alerts.add(
                DashboardTradeAlert(
                    id = "recall_${entry.recall.id}",
                    title = "Batch Recall: ${entry.recall.productName}",
                    message = "Batches ${entry.recall.batchNumbersCsv} — ${entry.recall.reason}",
                    category = TradeAlertCategory.RECALL,
                    severity = severity,
                    timestampEpochMillis = entry.recall.recallDateEpochMillis,
                    isUnread = true
                )
            )
        }

        // 2. Market shortage risk alerts (HIGH / MEDIUM)
        shortages.filter {
            it.riskLevel == ShortageRiskLevel.HIGH || it.riskLevel == ShortageRiskLevel.MEDIUM
        }.forEach { risk ->
            val severity = if (risk.riskLevel == ShortageRiskLevel.HIGH) {
                TradeAlertSeverity.CRITICAL
            } else {
                TradeAlertSeverity.WARNING
            }
            val reasonText = risk.triggeredReasons.firstOrNull()
                ?: "${risk.availableSuppliersCount} suppliers • ${risk.totalAvailableQuantity} units remaining"
            alerts.add(
                DashboardTradeAlert(
                    id = "shortage_${risk.productId}",
                    title = "${risk.riskLevel.label}: ${risk.productName}",
                    message = reasonText,
                    category = TradeAlertCategory.SHORTAGE,
                    severity = severity,
                    timestampEpochMillis = risk.lastUpdatedEpochMillis,
                    isUnread = true
                )
            )
        }

        // 3. Critical real-time batch stock / expiry alerts (ONLY for authorized Manufacturer & Brand users)
        authorizedInventoryItems.filter { it.isCritical }.take(4).forEach { stock ->
            val isExpiry = stock.daysUntilExpiry <= 30L
            val flagText = stock.criticalFlags.firstOrNull()
                ?: if (isExpiry) "Expires in ${stock.daysUntilExpiry}d" else "${stock.remainingQuantity} units remaining"
            alerts.add(
                DashboardTradeAlert(
                    id = "inv_${stock.id}",
                    title = "${stock.productName} (${stock.batchNumber})",
                    message = "$flagText • ${stock.stockStatus.label}",
                    category = if (isExpiry) TradeAlertCategory.EXPIRY else TradeAlertCategory.INVENTORY,
                    severity = if (stock.remainingQuantity == 0 || stock.daysUntilExpiry <= 15L || stock.isRecalled) {
                        TradeAlertSeverity.CRITICAL
                    } else {
                        TradeAlertSeverity.WARNING
                    },
                    timestampEpochMillis = System.currentTimeMillis(),
                    isUnread = true
                )
            )
        }

        // 4. Unread user-scoped trade notifications
        notifications.filter { !it.isRead }.take(8).forEach { notif ->
            val type = try {
                NotificationType.valueOf(notif.type)
            } catch (_: Exception) {
                NotificationType.ORDER_STATUS_CHANGED
            }
            val (category, severity) = when (type) {
                NotificationType.RECALL_ALERT -> TradeAlertCategory.RECALL to TradeAlertSeverity.CRITICAL
                NotificationType.EXPIRY_ALERT -> TradeAlertCategory.EXPIRY to TradeAlertSeverity.WARNING
                NotificationType.SHORTAGE_RISK -> TradeAlertCategory.SHORTAGE to TradeAlertSeverity.CRITICAL
                NotificationType.RETURN_UPDATE -> TradeAlertCategory.TRADE to TradeAlertSeverity.WARNING
                NotificationType.MEDICINE_SUBMITTED,
                NotificationType.MEDICINE_APPROVED,
                NotificationType.MEDICINE_REJECTED,
                NotificationType.BANNER_PUBLISHED,
                NotificationType.MEDICINE_ANNOUNCEMENT -> TradeAlertCategory.TRADE to TradeAlertSeverity.INFO
                NotificationType.NEW_ORDER_RECEIVED,
                NotificationType.ORDER_STATUS_CHANGED -> TradeAlertCategory.ORDER to TradeAlertSeverity.INFO
            }
            alerts.add(
                DashboardTradeAlert(
                    id = "notif_${notif.id}",
                    notificationId = notif.id,
                    title = notif.title,
                    message = notif.message,
                    category = category,
                    severity = severity,
                    timestampEpochMillis = notif.createdAtEpochMillis,
                    isUnread = !notif.isRead
                )
            )
        }

        return alerts.sortedWith(
            compareBy<DashboardTradeAlert> { it.severity.priority }
                .thenByDescending { it.timestampEpochMillis }
        )
    }

    fun selectAlertFilter(severity: TradeAlertSeverity?) {
        _uiState.update { it.copy(selectedAlertFilter = severity) }
    }

    fun markTradeAlertRead(alert: DashboardTradeAlert) {
        val notifId = alert.notificationId ?: return
        viewModelScope.launch {
            repository.patchApiNotificationRead(notifId)
        }
    }

    fun adjustInventoryStock(sku: String, delta: Int) {
        if (!_uiState.value.isRealTimeInventoryAuthorized) {
            _uiState.update {
                it.copy(errorMessage = "Access denied (403): Real-Time Inventory is restricted to Brand and Manufacturer accounts.")
            }
            return
        }
        viewModelScope.launch {
            val item = repository.getProductInventoryBySku(sku) ?: return@launch
            val newStock = (item.currentStock + delta).coerceAtLeast(0)
            repository.updateProductInventoryStock(sku, newStock)
        }
    }

    fun reorderProduct(item: ProductInventoryEntity, quantity: Int) {
        viewModelScope.launch {
            val productId = item.productId ?: 1L
            repository.quickRestockToBasket(productId, quantity)
            _uiState.update {
                it.copy(reorderSuccessMessage = "Reorder placed: $quantity units of ${item.productName} added to basket.")
            }
        }
    }

    fun clearReorderMessage() {
        _uiState.update { it.copy(reorderSuccessMessage = null) }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.logout()
            } catch (_: Exception) { }
            onLoggedOut()
        }
    }
}
