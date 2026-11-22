package com.example.ui.screens.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.BatchEntity
import com.example.domain.model.AuthUser
import com.example.domain.model.ProductDetail
import com.example.domain.model.ExpiryRiskStatus
import com.example.domain.model.InventoryDashboardSummary
import com.example.domain.model.InventoryStockItem
import com.example.domain.model.RecallSeverity
import com.example.domain.model.StockLevelStatus
import com.example.domain.model.StorageCondition
import com.example.domain.repository.BatchWithProduct
import com.example.domain.repository.PharmaRepository
import com.example.domain.repository.RecallWithAffectedStock
import com.example.service.InventoryMonitorManager
import com.example.service.InventoryMonitorState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class InventoryTab(val label: String) {
    ALL("All Stock"),
    CRITICAL("🚨 Critical Flags"),
    OUT_OF_STOCK("Out of Stock"),
    LOW_STOCK("Low Stock"),
    EXPIRING_SOON("Expiring Soon"),
    COLD_CHAIN("Cold Chain")
}

enum class InventorySort(val label: String) {
    CRITICAL_FIRST("Priority (Critical First)"),
    STOCK_LOW_HIGH("Stock: Low to High"),
    STOCK_HIGH_LOW("Stock: High to Low"),
    EXPIRY_URGENT("Expiry: Most Urgent"),
    VALUATION_HIGH("Value: High to Low"),
    NAME_AZ("Name: A to Z")
}

data class InventoryUiState(
    val isLoading: Boolean = true,
    val isAuthResolved: Boolean = false,
    val isInventoryAuthorized: Boolean = false,
    val isRefreshing: Boolean = false,
    val isBackendConnected: Boolean = false,
    val loadError: String? = null,
    val currentUser: AuthUser? = null,
    val allBatches: List<BatchWithProduct> = emptyList(),
    val filteredBatches: List<BatchWithProduct> = emptyList(),
    val searchQuery: String = "",
    val selectedFilter: BatchFilter = BatchFilter.ALL,
    val recalls: List<RecallWithAffectedStock> = emptyList(),
    val catalogueProducts: List<ProductDetail> = emptyList(),
    // Real-Time Inventory Dashboard additions:
    val inventoryItems: List<InventoryStockItem> = emptyList(),
    val filteredInventoryItems: List<InventoryStockItem> = emptyList(),
    val inventorySummary: InventoryDashboardSummary = InventoryDashboardSummary(),
    val selectedTab: InventoryTab = InventoryTab.ALL,
    val selectedSort: InventorySort = InventorySort.CRITICAL_FIRST,
    val monitorState: InventoryMonitorState = InventoryMonitorState(),
    val isRestocking: Boolean = false,
    val isScanning: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

enum class BatchFilter(val label: String) {
    ALL("All Batches"),
    EXPIRED("Expired"),
    NEAR_30("≤30 Days"),
    NEAR_90("≤90 Days"),
    OK("OK (>90 Days)")
}

class InventoryViewModel(
    private val repository: PharmaRepository,
    private val monitorManager: InventoryMonitorManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    private var lastSyncedUserId: Long? = null

    init {
        loadData()
        observeMonitor()
    }

    private fun observeMonitor() {
        if (monitorManager != null) {
            viewModelScope.launch {
                monitorManager.monitorState.collect { mState ->
                    _uiState.update { current ->
                        if (current.isInventoryAuthorized) {
                            current.copy(monitorState = mState)
                        } else {
                            current.copy(monitorState = InventoryMonitorState(isMonitoringActive = false))
                        }
                    }
                }
            }
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            repository.getApiUsersMeFlow().collect { user ->
                val authorized = user?.role?.canAccessRealTimeInventory == true
                val backendConnected = if (authorized) {
                    try {
                        repository.isProductionBackendConnected()
                    } catch (_: Exception) {
                        false
                    }
                } else {
                    false
                }

                if (!authorized) {
                    lastSyncedUserId = null
                    _uiState.update {
                        it.copy(
                            isAuthResolved = true,
                            isInventoryAuthorized = false,
                            isLoading = false,
                            isRefreshing = false,
                            isBackendConnected = false,
                            loadError = null,
                            currentUser = user,
                            allBatches = emptyList(),
                            filteredBatches = emptyList(),
                            inventoryItems = emptyList(),
                            filteredInventoryItems = emptyList(),
                            inventorySummary = InventoryDashboardSummary(),
                            monitorState = InventoryMonitorState(isMonitoringActive = false)
                        )
                    }
                } else {
                    val shouldRefresh = user != null && lastSyncedUserId != user.id
                    _uiState.update {
                        it.copy(
                            isAuthResolved = true,
                            isInventoryAuthorized = true,
                            isBackendConnected = backendConnected,
                            currentUser = user
                        )
                    }
                    if (shouldRefresh) {
                        lastSyncedUserId = user?.id
                        refreshInventory()
                    }
                }
            }
        }
        viewModelScope.launch {
            combine(
                repository.getApiUsersMeFlow(),
                repository.getApiBatchesFlow()
            ) { user, list ->
                val authorized = user?.role?.canAccessRealTimeInventory == true
                authorized to if (authorized) list else emptyList()
            }.collect { (authorized, list) ->
                _uiState.update { current ->
                    if (!authorized) {
                        current.copy(
                            allBatches = emptyList(),
                            filteredBatches = emptyList()
                        )
                    } else {
                        current.copy(
                            isLoading = current.isRefreshing && list.isEmpty() && current.inventoryItems.isEmpty(),
                            allBatches = list,
                            filteredBatches = applyFilter(list, current.searchQuery, current.selectedFilter)
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            combine(
                repository.getApiUsersMeFlow(),
                repository.getRealTimeInventoryFlow()
            ) { user, items ->
                val authorized = user?.role?.canAccessRealTimeInventory == true
                authorized to if (authorized) items else emptyList()
            }.collect { (authorized, items) ->
                _uiState.update { current ->
                    if (!authorized) {
                        current.copy(
                            inventoryItems = emptyList(),
                            filteredInventoryItems = emptyList()
                        )
                    } else {
                        current.copy(
                            isLoading = current.isRefreshing && items.isEmpty(),
                            inventoryItems = items,
                            filteredInventoryItems = applyInventoryFilterAndSort(
                                items = items,
                                query = current.searchQuery,
                                tab = current.selectedTab,
                                sort = current.selectedSort
                            )
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            combine(
                repository.getApiUsersMeFlow(),
                repository.getInventorySummaryFlow()
            ) { user, summary ->
                val authorized = user?.role?.canAccessRealTimeInventory == true
                if (authorized) summary else InventoryDashboardSummary()
            }.collect { summary ->
                _uiState.update { it.copy(inventorySummary = summary) }
            }
        }
        viewModelScope.launch {
            repository.getApiRecallsFlow().collect { list ->
                _uiState.update { it.copy(recalls = list) }
            }
        }
        viewModelScope.launch {
            repository.getApiProductsFlow().collect { prods ->
                _uiState.update { it.copy(catalogueProducts = prods) }
            }
        }
    }

    fun refreshInventory() {
        val current = _uiState.value
        if (current.isAuthResolved && !current.isInventoryAuthorized) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = "Access denied (403): Real-Time Inventory is restricted to Brand and Manufacturer accounts."
                )
            }
            return
        }

        viewModelScope.launch {
            val backendConnected = try {
                repository.isProductionBackendConnected()
            } catch (_: Exception) {
                false
            }
            _uiState.update {
                it.copy(
                    isRefreshing = true,
                    isBackendConnected = backendConnected,
                    isLoading = it.inventoryItems.isEmpty(),
                    loadError = null
                )
            }

            val result = repository.refreshRealTimeInventory()
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            isLoading = false,
                            loadError = null
                        )
                    }
                },
                onFailure = { err ->
                    val msg = err.message ?: "Failed to refresh real-time inventory"
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            isLoading = false,
                            loadError = msg,
                            errorMessage = msg
                        )
                    }
                }
            )
        }
    }

    fun onSearchQueryChanged(q: String) {
        _uiState.update {
            it.copy(
                searchQuery = q,
                filteredBatches = applyFilter(it.allBatches, q, it.selectedFilter),
                filteredInventoryItems = applyInventoryFilterAndSort(
                    items = it.inventoryItems,
                    query = q,
                    tab = it.selectedTab,
                    sort = it.selectedSort
                )
            )
        }
    }

    fun setFilter(filter: BatchFilter) {
        _uiState.update {
            it.copy(
                selectedFilter = filter,
                filteredBatches = applyFilter(it.allBatches, it.searchQuery, filter)
            )
        }
    }

    fun onInventoryTabChanged(tab: InventoryTab) {
        _uiState.update {
            it.copy(
                selectedTab = tab,
                filteredInventoryItems = applyInventoryFilterAndSort(
                    items = it.inventoryItems,
                    query = it.searchQuery,
                    tab = tab,
                    sort = it.selectedSort
                )
            )
        }
    }

    fun onInventorySortChanged(sort: InventorySort) {
        _uiState.update {
            it.copy(
                selectedSort = sort,
                filteredInventoryItems = applyInventoryFilterAndSort(
                    items = it.inventoryItems,
                    query = it.searchQuery,
                    tab = it.selectedTab,
                    sort = sort
                )
            )
        }
    }

    fun adjustBatchStock(batchId: Long, newQuantity: Int, reason: String, onDone: () -> Unit = {}) {
        if (!_uiState.value.isInventoryAuthorized) {
            _uiState.update {
                it.copy(errorMessage = "Access denied (403): Real-Time Inventory is restricted to Brand and Manufacturer accounts.")
            }
            return
        }
        viewModelScope.launch {
            val result = repository.adjustBatchStock(batchId, newQuantity, reason)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            loadError = null,
                            successMessage = "Stock updated ($newQuantity units)"
                        )
                    }
                    onDone()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "Failed to adjust stock") }
                }
            )
        }
    }

    fun quickRestock(item: InventoryStockItem, quantity: Int = 50, onDone: () -> Unit = {}) {
        if (!_uiState.value.isInventoryAuthorized) {
            _uiState.update {
                it.copy(errorMessage = "Access denied (403): Real-Time Inventory is restricted to Brand and Manufacturer accounts.")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isRestocking = true) }
            val result = repository.quickRestockToBasket(item.productId, quantity)
            _uiState.update { it.copy(isRestocking = false) }
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(successMessage = "Added restock order of $quantity units for ${item.productName} to Cart!") }
                    onDone()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "Restock failed") }
                }
            )
        }
    }

    fun triggerManualScan() {
        if (!_uiState.value.isInventoryAuthorized) {
            _uiState.update {
                it.copy(errorMessage = "Access denied (403): Real-Time Inventory is restricted to Brand and Manufacturer accounts.")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            val count = monitorManager?.runManualDepletionCheck() ?: 0
            _uiState.update {
                it.copy(
                    isScanning = false,
                    successMessage = "Depletion scan complete! Verified $count critical/depleted medicine(s)."
                )
            }
        }
    }

    fun updateCriticalThreshold(threshold: Int) {
        if (!_uiState.value.isInventoryAuthorized) {
            _uiState.update {
                it.copy(errorMessage = "Access denied (403): Real-Time Inventory is restricted to Brand and Manufacturer accounts.")
            }
            return
        }
        monitorManager?.setCriticalThreshold(threshold)
        _uiState.update {
            it.copy(successMessage = "Critical stock depletion threshold updated to $threshold units")
        }
    }

    fun toggleAutoNotifications(enabled: Boolean) {
        if (!_uiState.value.isInventoryAuthorized) {
            _uiState.update {
                it.copy(errorMessage = "Access denied (403): Real-Time Inventory is restricted to Brand and Manufacturer accounts.")
            }
            return
        }
        monitorManager?.setNotificationsEnabled(enabled)
        _uiState.update {
            it.copy(successMessage = if (enabled) "Automated stock depletion push alerts enabled" else "Automated push alerts paused")
        }
    }

    fun toggleMonitoring(active: Boolean) {
        if (!_uiState.value.isInventoryAuthorized) {
            _uiState.update {
                it.copy(errorMessage = "Access denied (403): Real-Time Inventory is restricted to Brand and Manufacturer accounts.")
            }
            return
        }
        if (active) {
            monitorManager?.startMonitoring()
            _uiState.update { it.copy(successMessage = "Inventory depletion monitoring activated") }
        } else {
            monitorManager?.stopMonitoring()
            _uiState.update { it.copy(successMessage = "Inventory depletion monitoring paused") }
        }
    }

    fun createBatch(batch: BatchEntity, onComplete: () -> Unit) {
        if (!_uiState.value.isInventoryAuthorized) {
            _uiState.update {
                it.copy(errorMessage = "Access denied (403): Real-Time Inventory is restricted to Brand and Manufacturer accounts.")
            }
            return
        }
        viewModelScope.launch {
            val result = repository.postApiBatches(batch)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            loadError = null,
                            successMessage = "Batch added successfully to warehouse inventory!"
                        )
                    }
                    onComplete()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "Failed to add batch") }
                }
            )
        }
    }

    fun createRecall(
        productId: Long,
        batchNumbersCsv: String,
        reason: String,
        severity: RecallSeverity,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.postApiRecalls(productId, batchNumbersCsv, reason, severity.name)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(successMessage = "Batch recall notice published and buyers alerted!") }
                    onComplete()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "Failed to publish recall") }
                }
            )
        }
    }

    private fun applyFilter(list: List<BatchWithProduct>, q: String, filter: BatchFilter): List<BatchWithProduct> {
        val query = q.trim().lowercase()
        return list.filter { b ->
            val matchesQuery = query.isEmpty() ||
                b.batch.batchNumber.lowercase().contains(query) ||
                b.productName.lowercase().contains(query) ||
                b.brand.lowercase().contains(query)

            val matchesFilter = when (filter) {
                BatchFilter.ALL -> true
                BatchFilter.EXPIRED -> b.daysUntilExpiry < 0
                BatchFilter.NEAR_30 -> b.daysUntilExpiry in 0..30
                BatchFilter.NEAR_90 -> b.daysUntilExpiry in 0..90
                BatchFilter.OK -> b.daysUntilExpiry > 90
            }

            matchesQuery && matchesFilter
        }
    }

    private fun applyInventoryFilterAndSort(
        items: List<InventoryStockItem>,
        query: String,
        tab: InventoryTab,
        sort: InventorySort
    ): List<InventoryStockItem> {
        val q = query.trim().lowercase()
        val filtered = items.filter { item ->
            val matchesQuery = q.isEmpty() ||
                item.productName.lowercase().contains(q) ||
                item.brand.lowercase().contains(q) ||
                item.saltComposition.lowercase().contains(q) ||
                item.batchNumber.lowercase().contains(q) ||
                item.therapeuticCategory.lowercase().contains(q)

            val matchesTab = when (tab) {
                InventoryTab.ALL -> true
                InventoryTab.CRITICAL -> item.isCritical
                InventoryTab.OUT_OF_STOCK -> item.stockStatus == StockLevelStatus.OUT_OF_STOCK
                InventoryTab.LOW_STOCK -> item.stockStatus == StockLevelStatus.LOW_STOCK || item.stockStatus == StockLevelStatus.CRITICAL_LOW
                InventoryTab.EXPIRING_SOON -> item.expiryStatus == ExpiryRiskStatus.EXPIRED ||
                    item.expiryStatus == ExpiryRiskStatus.EXPIRING_CRITICAL ||
                    item.expiryStatus == ExpiryRiskStatus.EXPIRING_WARNING
                InventoryTab.COLD_CHAIN -> item.storageCondition == StorageCondition.REFRIGERATED_2_8 ||
                    item.storageCondition == StorageCondition.FROZEN
            }

            matchesQuery && matchesTab
        }

        return when (sort) {
            InventorySort.CRITICAL_FIRST -> filtered.sortedWith(
                compareByDescending<InventoryStockItem> { it.isCritical }
                    .thenBy { it.remainingQuantity }
                    .thenBy { it.daysUntilExpiry }
            )
            InventorySort.STOCK_LOW_HIGH -> filtered.sortedBy { it.remainingQuantity }
            InventorySort.STOCK_HIGH_LOW -> filtered.sortedByDescending { it.remainingQuantity }
            InventorySort.EXPIRY_URGENT -> filtered.sortedBy { it.daysUntilExpiry }
            InventorySort.VALUATION_HIGH -> filtered.sortedByDescending { it.totalValuation }
            InventorySort.NAME_AZ -> filtered.sortedBy { it.productName.lowercase() }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
