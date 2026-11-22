package com.example.ui.screens.orders

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.OrderEntity
import com.example.domain.model.AuthUser
import com.example.domain.model.OrderStatus
import com.example.domain.model.TaxInvoiceData
import com.example.domain.model.UserRole
import com.example.domain.repository.BasketWithDetails
import com.example.domain.repository.OrderDetailsWithHistory
import com.example.domain.repository.PharmaRepository
import com.example.util.Formatters
import com.example.util.TaxInvoicePdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

enum class PipelineStatusType(val displayName: String, val bgHex: Long, val textHex: Long) {
    IN_TRANSIT("In Transit", 0xFFDBEAFE, 0xFF1E40AF),
    QA_CHECK("QA Check", 0xFFE0F2FE, 0xFF0369A1),
    STAGING("Staging", 0xFFF1F5F9, 0xFF475569),
    UNDER_PRODUCTION("Under Production", 0xFFFEF3C7, 0xFF92400E),
    READY_FOR_QC("Ready for QC", 0xFFE0E7FF, 0xFF3730A3),
    DISPATCHED("Dispatched", 0xFFDCFCE7, 0xFF166534),
    DELIVERED("Delivered", 0xFFDCFCE7, 0xFF15803D)
}

enum class PipelineIconKind {
    CAPSULE,
    VIAL,
    PALLET
}

data class OrderPipelineItem(
    val id: Long,
    val orderNumber: String,
    val buyerName: String,
    val productSummary: String,
    val status: PipelineStatusType,
    val stageName: String,
    val progressPercent: Int,
    val tag1: String, // e.g. "BlueDart", "+DT-0020", "₹8.5L"
    val tag2: String, // e.g. "4.2°C", "COA Verified", "12 Pallets"
    val tag3: String, // e.g. "Tomorrow, 11:30 AM", "19 Nov, 9 AM", "Bay 3"
    val actionText: String, // e.g. "Track >", "Dossier >", "Manage >"
    val iconKind: PipelineIconKind,
    val isColdChain: Boolean = false,
    val rawOrderId: Long? = null
)

data class PipelineKpis(
    val activeOrders: Int = 0,
    val activeOrdersTrend: String = "0 today",
    val pendingQc: Int = 0,
    val pendingQcAudits: String = "0 audits",
    val dispatchedLots: Int = 0,
    val dispatchedOnTime: String = "On-time",
    val dispatchValueRupeesLakhs: String = "₹0",
    val dispatchTargetPercent: String = "Live"
)

data class OrdersUiState(
    val isLoading: Boolean = true,
    val currentUser: AuthUser? = null,
    val isLivePipelineAuthorized: Boolean = false,
    val orders: List<OrderEntity> = emptyList(),
    val pipelineItems: List<OrderPipelineItem> = emptyList(),
    val filteredPipelineItems: List<OrderPipelineItem> = emptyList(),
    val activeConsignments: List<OrderPipelineItem> = emptyList(),
    val kpis: PipelineKpis = PipelineKpis(),
    val searchQuery: String = "",
    val selectedFilterTab: String = "All Orders",
    val selectedSort: String = "Sort by ETA",
    val selectedOrderDetails: OrderDetailsWithHistory? = null,
    val basketForCheckout: BasketWithDetails? = null,
    val isCheckoutBasketLoading: Boolean = false,
    val placedOrderIds: List<Long> = emptyList(),
    val isPlacingOrder: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val transactions: List<com.example.ui.components.OrderTransactionUiModel> = emptyList(),
    val activeViewMode: String = "HISTORY", // "HISTORY" (Past Transactions) or "PIPELINE"
    val cartItemCount: Int = 0,
    val basketItemsCount: Int = 0,
    val taxInvoice: TaxInvoiceData? = null,
    val isLoadingTaxInvoice: Boolean = false,
    val taxInvoiceError: String? = null,
    val isDownloadingTaxInvoice: Boolean = false,
    val lastSavedInvoicePath: String? = null
)

class OrdersViewModel(
    private val repository: PharmaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrdersUiState())
    val uiState: StateFlow<OrdersUiState> = _uiState.asStateFlow()

    private var orderDetailsJob: Job? = null
    private val isOrderSubmissionInFlight = AtomicBoolean(false)

    init {
        viewModelScope.launch {
            repository.ensureDefaultOrdersSeeded()
            loadCurrentUser()
            observeOrders()
            observeTransactions()
            observeBasket()
        }
    }

    private fun observeBasket() {
        viewModelScope.launch {
            repository.getApiBasketFlow().collect { basket ->
                _uiState.update {
                    it.copy(
                        basketForCheckout = basket,
                        isCheckoutBasketLoading = false,
                        cartItemCount = basket.items.sumOf { item -> item.quantity },
                        basketItemsCount = basket.items.size
                    )
                }
            }
        }
    }

    private fun observeTransactions() {
        viewModelScope.launch {
            repository.getOrderHistoryTransactionsFlow().collect { list ->
                _uiState.update { it.copy(transactions = list) }
            }
        }
    }

    fun setActiveViewMode(mode: String) {
        val authorized = _uiState.value.isLivePipelineAuthorized
        val targetMode = if (mode == "PIPELINE" && authorized) "PIPELINE" else "HISTORY"
        _uiState.update { it.copy(activeViewMode = targetMode) }
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            repository.getApiUsersMeFlow().collect { user ->
                val authorized = user != null && (user.role.isSupplier || user.role == UserRole.ADMIN)
                _uiState.update { current ->
                    current.copy(
                        currentUser = user,
                        isLivePipelineAuthorized = authorized,
                        activeViewMode = if (!authorized && current.activeViewMode == "PIPELINE") "HISTORY" else current.activeViewMode
                    )
                }
            }
        }
    }

    fun observeOrders() {
        viewModelScope.launch {
            repository.getApiLivePipelineOrdersFlow().collect { list ->
                val pipeline = mapOrdersToPipeline(list)
                val filtered = applyFilterAndSort(
                    pipeline,
                    _uiState.value.searchQuery,
                    _uiState.value.selectedFilterTab,
                    _uiState.value.selectedSort
                )
                val consignments = pipeline.filter {
                    it.status == PipelineStatusType.IN_TRANSIT ||
                        it.status == PipelineStatusType.QA_CHECK ||
                        it.status == PipelineStatusType.STAGING
                }
                val activeCount = list.count {
                    it.status != OrderStatus.DELIVERED.name && it.status != OrderStatus.CANCELLED.name
                }
                val pendingQcCount = list.count { it.status == OrderStatus.PACKED.name }
                val dispatchedCount = list.count {
                    it.status == OrderStatus.DISPATCHED.name || it.status == OrderStatus.DELIVERED.name
                }
                val totalVal = list.sumOf { it.totalAmount }
                val kpis = PipelineKpis(
                    activeOrders = activeCount,
                    activeOrdersTrend = "$activeCount live",
                    pendingQc = pendingQcCount,
                    pendingQcAudits = "$pendingQcCount lots",
                    dispatchedLots = dispatchedCount,
                    dispatchedOnTime = if (dispatchedCount > 0) "Active" else "None",
                    dispatchValueRupeesLakhs = Formatters.formatInr(totalVal),
                    dispatchTargetPercent = "${list.size} orders"
                )
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        orders = list,
                        pipelineItems = pipeline,
                        filteredPipelineItems = filtered,
                        activeConsignments = consignments,
                        kpis = kpis
                    )
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { current ->
            val filtered = applyFilterAndSort(current.pipelineItems, query, current.selectedFilterTab, current.selectedSort)
            current.copy(searchQuery = query, filteredPipelineItems = filtered)
        }
    }

    fun onClearSearchQuery() {
        onSearchQueryChanged("")
    }

    fun selectFilterTab(tab: String) {
        _uiState.update { current ->
            val filtered = applyFilterAndSort(current.pipelineItems, current.searchQuery, tab, current.selectedSort)
            current.copy(selectedFilterTab = tab, filteredPipelineItems = filtered)
        }
    }

    fun selectSortOption(sort: String) {
        _uiState.update { current ->
            val filtered = applyFilterAndSort(current.pipelineItems, current.searchQuery, current.selectedFilterTab, sort)
            current.copy(selectedSort = sort, filteredPipelineItems = filtered)
        }
    }

    private fun applyFilterAndSort(
        items: List<OrderPipelineItem>,
        query: String,
        tab: String,
        sort: String
    ): List<OrderPipelineItem> {
        var result = items

        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            result = result.filter { item ->
                item.buyerName.lowercase().contains(q) ||
                    item.orderNumber.lowercase().contains(q) ||
                    item.productSummary.lowercase().contains(q) ||
                    item.tag1.lowercase().contains(q) ||
                    item.status.displayName.lowercase().contains(q)
            }
        }

        result = when {
            tab.contains("Under Production", ignoreCase = true) ->
                result.filter { it.status == PipelineStatusType.UNDER_PRODUCTION || it.status == PipelineStatusType.STAGING }
            tab.contains("Ready for QC", ignoreCase = true) ->
                result.filter { it.status == PipelineStatusType.READY_FOR_QC || it.status == PipelineStatusType.QA_CHECK }
            tab.contains("In Transit", ignoreCase = true) ->
                result.filter { it.status == PipelineStatusType.IN_TRANSIT }
            tab.contains("Dispatched", ignoreCase = true) ->
                result.filter { it.status == PipelineStatusType.DISPATCHED }
            else -> result
        }

        return when (sort) {
            "Sort by Progress" -> result.sortedByDescending { it.progressPercent }
            "Sort by Order ID" -> result.sortedByDescending { it.orderNumber }
            else -> result
        }
    }

    private fun mapOrdersToPipeline(orders: List<OrderEntity>): List<OrderPipelineItem> {
        return orders.map { order ->
            val statusType = when (order.status) {
                OrderStatus.PLACED.name -> PipelineStatusType.UNDER_PRODUCTION
                OrderStatus.ACCEPTED.name -> PipelineStatusType.STAGING
                OrderStatus.PACKED.name -> PipelineStatusType.READY_FOR_QC
                OrderStatus.DISPATCHED.name -> PipelineStatusType.IN_TRANSIT
                OrderStatus.DELIVERED.name -> PipelineStatusType.DELIVERED
                else -> PipelineStatusType.UNDER_PRODUCTION
            }
            val (stage, progress) = when (order.status) {
                OrderStatus.PLACED.name -> "Order Acknowledged" to 15
                OrderStatus.ACCEPTED.name -> "Pallet Picking" to 35
                OrderStatus.PACKED.name -> "Serialization & QC" to 60
                OrderStatus.DISPATCHED.name -> "Dispatched to Carrier" to 85
                OrderStatus.DELIVERED.name -> "Delivered & Verified" to 100
                else -> "In Process" to 20
            }
            val courier = order.transporterName?.takeIf { it.isNotBlank() } ?: "Assigned Logistics"
            val eta = if (order.expectedDeliveryEpochMillis != null) {
                Formatters.formatDate(order.expectedDeliveryEpochMillis)
            } else {
                "Scheduled"
            }

            OrderPipelineItem(
                id = order.id,
                orderNumber = order.orderNumber,
                buyerName = "Buyer #${order.buyerId}",
                productSummary = order.deliveryAddress.ifBlank { "Order ${order.orderNumber}" },
                status = statusType,
                stageName = stage,
                progressPercent = progress,
                tag1 = courier,
                tag2 = Formatters.formatInr(order.totalAmount),
                tag3 = eta,
                actionText = if (statusType == PipelineStatusType.IN_TRANSIT) "Track >" else "Manage >",
                iconKind = PipelineIconKind.CAPSULE,
                isColdChain = false,
                rawOrderId = order.id
            )
        }
    }

    fun loadBasketForCheckout() {
        viewModelScope.launch {
            if (_uiState.value.basketForCheckout == null) {
                _uiState.update { it.copy(isCheckoutBasketLoading = true) }
            }
            val b = repository.getApiBasket()
            _uiState.update {
                it.copy(
                    basketForCheckout = b,
                    isCheckoutBasketLoading = false,
                    cartItemCount = b.items.sumOf { item -> item.quantity },
                    basketItemsCount = b.items.size
                )
            }
        }
    }

    fun placeOrders(
        deliveryAddress: String,
        notes: String,
        onFailure: (String) -> Unit = {},
        onSuccess: (List<Long>) -> Unit
    ) {
        if (!isOrderSubmissionInFlight.compareAndSet(false, true)) {
            onFailure("Order submission is already in progress.")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isPlacingOrder = true, errorMessage = null, successMessage = null) }
            try {
                val result = repository.postApiOrders(deliveryAddress, notes)
                result.fold(
                    onSuccess = { ids ->
                        if (ids.isEmpty()) {
                            val msg = "Order creation could not be confirmed. Please try again."
                            _uiState.update { it.copy(isPlacingOrder = false, errorMessage = msg) }
                            onFailure(msg)
                        } else {
                            val refreshedBasket = repository.getApiBasket()
                            _uiState.update {
                                it.copy(
                                    isPlacingOrder = false,
                                    placedOrderIds = ids,
                                    basketForCheckout = refreshedBasket,
                                    errorMessage = null
                                )
                            }
                            onSuccess(ids)
                        }
                    },
                    onFailure = { err ->
                        val msg = err.message ?: "Failed to place order"
                        _uiState.update { it.copy(isPlacingOrder = false, errorMessage = msg) }
                        onFailure(msg)
                    }
                )
            } finally {
                isOrderSubmissionInFlight.set(false)
            }
        }
    }

    fun loadOrderDetails(orderId: Long) {
        orderDetailsJob?.cancel()
        orderDetailsJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isLoadingTaxInvoice = true,
                    taxInvoiceError = null,
                    lastSavedInvoicePath = null
                )
            }
            repository.getApiOrderByIdFlow(orderId).collect { details ->
                _uiState.update { it.copy(isLoading = false, selectedOrderDetails = details) }
                if (details != null) {
                    val invRes = repository.getOrderTaxInvoice(orderId)
                    invRes.fold(
                        onSuccess = { inv ->
                            _uiState.update {
                                it.copy(
                                    taxInvoice = inv,
                                    isLoadingTaxInvoice = false,
                                    taxInvoiceError = null
                                )
                            }
                        },
                        onFailure = { err ->
                            _uiState.update {
                                it.copy(
                                    taxInvoice = null,
                                    isLoadingTaxInvoice = false,
                                    taxInvoiceError = err.message ?: "Unable to load tax invoice details."
                                )
                            }
                        }
                    )
                } else {
                    _uiState.update { it.copy(isLoadingTaxInvoice = false) }
                }
            }
        }
    }

    fun downloadTaxInvoicePdf(context: Context, orderId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloadingTaxInvoice = true, errorMessage = null) }
            try {
                val invoice = _uiState.value.taxInvoice
                    ?: repository.getOrderTaxInvoice(orderId).getOrThrow()
                if (!invoice.isEligible) {
                    throw IllegalStateException(
                        invoice.eligibilityReason ?: "Tax invoice is only available for packed, dispatched, or delivered orders."
                    )
                }
                val backendBytes = repository.downloadBackendTaxInvoicePdf(orderId).getOrNull()
                val saved = withContext(Dispatchers.IO) {
                    TaxInvoicePdfGenerator.saveToDownloads(context, invoice, backendBytes)
                }
                _uiState.update {
                    it.copy(
                        isDownloadingTaxInvoice = false,
                        lastSavedInvoicePath = saved.displayLocation,
                        successMessage = "Tax invoice PDF saved to ${saved.displayLocation}"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isDownloadingTaxInvoice = false,
                        errorMessage = e.message ?: "Failed to generate tax invoice PDF"
                    )
                }
            }
        }
    }

    fun shareTaxInvoicePdf(context: Context, orderId: Long, onIntentReady: (Intent) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloadingTaxInvoice = true, errorMessage = null) }
            try {
                val invoice = _uiState.value.taxInvoice
                    ?: repository.getOrderTaxInvoice(orderId).getOrThrow()
                if (!invoice.isEligible) {
                    throw IllegalStateException(
                        invoice.eligibilityReason ?: "Tax invoice is only available for packed, dispatched, or delivered orders."
                    )
                }
                val backendBytes = repository.downloadBackendTaxInvoicePdf(orderId).getOrNull()
                val shareIntent = withContext(Dispatchers.IO) {
                    val file = TaxInvoicePdfGenerator.generatePdfFile(context, invoice, backendBytes)
                    TaxInvoicePdfGenerator.createShareIntent(context, file, invoice)
                }
                _uiState.update { it.copy(isDownloadingTaxInvoice = false) }
                onIntentReady(shareIntent)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isDownloadingTaxInvoice = false,
                        errorMessage = e.message ?: "Failed to prepare tax invoice share sheet"
                    )
                }
            }
        }
    }

    fun updateOrderStatus(
        orderId: Long,
        newStatus: OrderStatus,
        note: String,
        dispatchEpochMillis: Long? = null,
        expectedDeliveryEpochMillis: Long? = null,
        ewayBillNumber: String? = null,
        vehicleNumber: String? = null,
        transporterName: String? = null
    ) {
        viewModelScope.launch {
            val result = repository.patchApiOrderStatus(
                orderId = orderId,
                newStatus = newStatus.name,
                note = note,
                dispatchEpochMillis = dispatchEpochMillis,
                expectedDeliveryEpochMillis = expectedDeliveryEpochMillis,
                ewayBillNumber = ewayBillNumber,
                vehicleNumber = vehicleNumber,
                transporterName = transporterName
            )
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(successMessage = "Order status updated to ${newStatus.displayName}") }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "Failed to update order status") }
                }
            )
        }
    }

    fun updateLineFulfillment(orderItemId: Long, fulfilledQty: Int, batchNumber: String?, expiryMillis: Long?) {
        viewModelScope.launch {
            repository.patchApiOrderItemFulfillment(orderItemId, fulfilledQty, batchNumber, expiryMillis)
        }
    }

    fun submitProofOfDelivery(
        orderId: Long,
        receivedByName: String,
        notes: String,
        photoUri: String?,
        shortQty: Int,
        damagedQty: Int,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.postApiOrderProofOfDelivery(orderId, receivedByName, notes, photoUri, shortQty, damagedQty)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(successMessage = "Proof of delivery recorded successfully!") }
                    onComplete()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "Failed to submit POD") }
                }
            )
        }
    }

    fun reorderPastOrder(orderId: Long, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val result = repository.postApiOrderReorder(orderId)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(successMessage = "Reorder items added to basket!") }
                    onSuccess()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "Failed to reorder") }
                }
            )
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
