package com.example.data.remote.source

import com.example.data.remote.dto.AddBasketItemRequestDto
import com.example.data.remote.dto.AdjustBatchStockRequestDto
import com.example.data.remote.dto.AdjustInventoryStockRequestDto
import com.example.data.remote.dto.BasketDto
import com.example.data.remote.dto.BasketLoadReportDto
import com.example.data.remote.dto.BatchDto
import com.example.data.remote.dto.ComplianceVerificationDto
import com.example.data.remote.dto.CreateOrderRequestDto
import com.example.data.remote.dto.CreateRecallRequestDto
import com.example.data.remote.dto.CreateReturnRequestDto
import com.example.data.remote.dto.CreditLedgerEntryDto
import com.example.data.remote.dto.CreditNoteDto
import com.example.data.remote.dto.DashboardStatsDto
import com.example.data.remote.dto.FulfillOrderItemRequestDto
import com.example.data.remote.dto.LedgerBalanceDto
import com.example.data.remote.dto.NamedBasketDto
import com.example.data.remote.dto.NotificationDto
import com.example.data.remote.dto.OptimizationResultDto
import com.example.data.remote.dto.OptimizeBasketRequestDto
import com.example.data.remote.dto.OrderDetailsDto
import com.example.data.remote.dto.OrderDto
import com.example.data.remote.dto.ProductDto
import com.example.data.remote.dto.ProductInventoryDto
import com.example.data.remote.dto.RecallDto
import com.example.data.remote.dto.RecordLedgerPaymentRequestDto
import com.example.data.remote.dto.ReturnDecisionRequestDto
import com.example.data.remote.dto.ReturnDto
import com.example.data.remote.dto.SaveNamedBasketRequestDto
import com.example.data.remote.dto.ShortageAlertDto
import com.example.data.remote.dto.SubmitPodRequestDto
import com.example.data.remote.dto.SupplierOfferDto
import com.example.data.remote.dto.SupplierSettingsDto
import com.example.data.remote.dto.TaxInvoiceDto
import com.example.data.remote.dto.TrustScoreDto
import com.example.data.remote.dto.UpdateBasketQtyRequestDto
import com.example.data.remote.dto.UpdateOrderStatusRequestDto
import com.example.data.remote.dto.UpdateProductStatusRequestDto
import com.example.data.remote.dto.UpdateReorderThresholdRequestDto
import com.example.data.remote.dto.UpdateUserStatusRequestDto
import com.example.data.remote.dto.UserDto
import com.example.data.remote.util.NetworkResult
import com.example.data.remote.util.safeApiCall
import com.example.data.remote.util.safeApiEnvelopeCall
import com.example.di.NetworkModule

/**
 * Remote Data Source bridging all remaining PharmaOS feature modules with the Spring Boot REST API.
 *
 * Every method validates [NetworkModule.isBackendConfigured] before firing network calls and
 * strictly returns [NetworkResult] without silently writing stale/fake data on remote failure.
 */
class PharmaRemoteDataSource(
    private val networkModule: NetworkModule
) {

    private fun <T> notConfiguredError(): NetworkResult<T> = NetworkResult.NotConfigured(
        "Spring Boot backend URL is not configured (${networkModule.getCurrentBaseUrl()})."
    )

    // 1. Users & Compliance
    suspend fun fetchCurrentUser(): NetworkResult<UserDto> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) { networkModule.userApiService().getCurrentUser() }
    }

    suspend fun fetchAllUsers(): NetworkResult<List<UserDto>> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) { networkModule.userApiService().getAllUsers() }
    }

    suspend fun updateUserStatus(userId: Long, status: String): NetworkResult<UserDto> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.userApiService().updateUserStatus(userId, UpdateUserStatusRequestDto(status))
        }
    }

    suspend fun fetchComplianceVerification(userId: Long): NetworkResult<ComplianceVerificationDto> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.userApiService().getComplianceVerification(userId)
        }
    }

    suspend fun upsertComplianceVerification(dto: ComplianceVerificationDto): NetworkResult<ComplianceVerificationDto> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.userApiService().upsertComplianceVerification(dto)
        }
    }

    // 2. Product Catalogue
    suspend fun fetchProducts(query: String? = null, activeOnly: Boolean = true): NetworkResult<List<ProductDto>> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.productApiService().getProducts(query = query, activeOnly = activeOnly)
        }
    }

    suspend fun fetchProductById(productId: Long): NetworkResult<ProductDto> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.productApiService().getProductById(productId)
        }
    }

    suspend fun fetchAlternativeBrands(productId: Long): NetworkResult<List<ProductDto>> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.productApiService().getAlternativeBrands(productId)
        }
    }

    suspend fun createProduct(dto: ProductDto): NetworkResult<ProductDto> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) { networkModule.productApiService().createProduct(dto) }
    }

    suspend fun updateProduct(productId: Long, dto: ProductDto): NetworkResult<ProductDto> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.productApiService().updateProduct(productId, dto)
        }
    }

    suspend fun updateProductStatus(productId: Long, isActive: Boolean): NetworkResult<Unit> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiCall(networkModule.moshi) {
            networkModule.productApiService().updateProductStatus(productId, UpdateProductStatusRequestDto(isActive))
        }
    }

    suspend fun deleteProduct(productId: Long): NetworkResult<Unit> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiCall(networkModule.moshi) { networkModule.productApiService().deleteProduct(productId) }
    }

    // 3. Supplier Offers & Settings
    suspend fun fetchOffersForProduct(productId: Long): NetworkResult<List<SupplierOfferDto>> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.supplierApiService().getOffersForProduct(productId)
        }
    }

    suspend fun fetchOffersBySupplier(supplierId: Long): NetworkResult<List<SupplierOfferDto>> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.supplierApiService().getOffersBySupplier(supplierId)
        }
    }

    suspend fun createOffer(dto: SupplierOfferDto): NetworkResult<SupplierOfferDto> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) { networkModule.supplierApiService().createOffer(dto) }
    }

    suspend fun updateOffer(offerId: Long, dto: SupplierOfferDto): NetworkResult<SupplierOfferDto> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.supplierApiService().updateOffer(offerId, dto)
        }
    }

    suspend fun updateOfferStatus(offerId: Long, isActive: Boolean): NetworkResult<Unit> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiCall(networkModule.moshi) {
            networkModule.supplierApiService().updateOfferStatus(offerId, UpdateProductStatusRequestDto(isActive))
        }
    }

    suspend fun fetchSupplierSettings(supplierId: Long): NetworkResult<SupplierSettingsDto> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.supplierApiService().getSupplierSettings(supplierId)
        }
    }

    suspend fun updateSupplierSettings(supplierId: Long, dto: SupplierSettingsDto): NetworkResult<SupplierSettingsDto> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.supplierApiService().updateSupplierSettings(supplierId, dto)
        }
    }

    // 4 & 5. Shopping Basket & Best-Buy Optimizer
    suspend fun fetchActiveBasket(): NetworkResult<BasketDto> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) { networkModule.basketApiService().getActiveBasket() }
    }

    suspend fun addBasketItem(productId: Long, offerId: Long, quantity: Int): NetworkResult<BasketDto> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.basketApiService().addBasketItem(AddBasketItemRequestDto(productId, offerId, quantity))
        }
    }

    suspend fun updateBasketItemQuantity(itemId: Long, quantity: Int): NetworkResult<BasketDto> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.basketApiService().updateBasketItemQuantity(itemId, UpdateBasketQtyRequestDto(quantity))
        }
    }

    suspend fun removeBasketItem(itemId: Long): NetworkResult<Unit> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiCall(networkModule.moshi) { networkModule.basketApiService().removeBasketItem(itemId) }
    }

    suspend fun clearBasket(): NetworkResult<Unit> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiCall(networkModule.moshi) { networkModule.basketApiService().clearBasket() }
    }

    suspend fun fetchSavedBaskets(): NetworkResult<List<NamedBasketDto>> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) { networkModule.basketApiService().getSavedBaskets() }
    }

    suspend fun saveNamedBasket(name: String): NetworkResult<NamedBasketDto> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.basketApiService().saveNamedBasket(SaveNamedBasketRequestDto(name))
        }
    }

    suspend fun loadSavedBasket(savedBasketId: Long): NetworkResult<BasketLoadReportDto> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.basketApiService().loadSavedBasket(savedBasketId)
        }
    }

    suspend fun optimizeBasket(preferReliableSuppliers: Boolean): NetworkResult<OptimizationResultDto> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.basketApiService().optimizeBasket(OptimizeBasketRequestDto(preferReliableSuppliers))
        }
    }

    suspend fun applyOptimization(resultDto: OptimizationResultDto): NetworkResult<BasketDto> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.basketApiService().applyOptimization(resultDto)
        }
    }

    // 6 & 7. Purchase Orders, Live Pipeline, Tax Invoice & Order Tracking
    suspend fun fetchOrders(supplierId: Long? = null): NetworkResult<List<OrderDto>> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) { networkModule.orderApiService().getOrders(supplierId) }
    }

    suspend fun fetchLivePipelineOrders(): NetworkResult<List<OrderDto>> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) { networkModule.orderApiService().getLivePipelineOrders() }
    }

    suspend fun fetchOrderById(orderId: Long): NetworkResult<OrderDetailsDto> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) { networkModule.orderApiService().getOrderById(orderId) }
    }

    suspend fun fetchOrderTaxInvoice(orderId: Long): NetworkResult<TaxInvoiceDto> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) { networkModule.orderApiService().getOrderTaxInvoice(orderId) }
    }

    suspend fun downloadOrderTaxInvoicePdf(orderId: Long): NetworkResult<ByteArray> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return when (val result = safeApiCall(networkModule.moshi) {
            networkModule.orderApiService().downloadOrderTaxInvoicePdf(orderId)
        }) {
            is NetworkResult.Success -> NetworkResult.Success(
                data = result.data.use { it.bytes() },
                httpCode = result.httpCode
            )
            is NetworkResult.HttpError -> result
            is NetworkResult.NetworkFailure -> result
            is NetworkResult.NotConfigured -> result
        }
    }

    suspend fun createOrdersFromBasket(
        deliveryAddress: String,
        notes: String,
        idempotencyKey: String? = null
    ): NetworkResult<List<OrderDto>> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.orderApiService().createOrdersFromBasket(
                CreateOrderRequestDto(
                    deliveryAddress = deliveryAddress,
                    notes = notes,
                    idempotencyKey = idempotencyKey
                )
            )
        }
    }

    suspend fun updateOrderStatus(orderId: Long, request: UpdateOrderStatusRequestDto): NetworkResult<OrderDto> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.orderApiService().updateOrderStatus(orderId, request)
        }
    }

    suspend fun updateOrderItemFulfillment(orderItemId: Long, request: FulfillOrderItemRequestDto): NetworkResult<Unit> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiCall(networkModule.moshi) {
            networkModule.orderApiService().updateOrderItemFulfillment(orderItemId, request)
        }
    }

    suspend fun submitProofOfDelivery(orderId: Long, request: SubmitPodRequestDto): NetworkResult<Unit> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiCall(networkModule.moshi) {
            networkModule.orderApiService().submitProofOfDelivery(orderId, request)
        }
    }

    suspend fun reorderPastOrder(orderId: Long): NetworkResult<Unit> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiCall(networkModule.moshi) { networkModule.orderApiService().reorderPastOrder(orderId) }
    }

    // 8. Inventory, Batches & Shortage Evaluation
    suspend fun fetchInventory(lowStockOnly: Boolean = false): NetworkResult<List<ProductInventoryDto>> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.inventoryApiService().getInventory(lowStockOnly)
        }
    }

    suspend fun upsertInventoryItem(dto: ProductInventoryDto): NetworkResult<ProductInventoryDto> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.inventoryApiService().upsertInventoryItem(dto)
        }
    }

    suspend fun adjustInventoryStock(
        sku: String,
        newStock: Int? = null,
        delta: Int? = null,
        reason: String = "Inventory Update"
    ): NetworkResult<ProductInventoryDto> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.inventoryApiService().adjustInventoryStock(
                sku,
                AdjustInventoryStockRequestDto(newStock = newStock, delta = delta, reason = reason)
            )
        }
    }

    suspend fun updateReorderThreshold(sku: String, threshold: Int): NetworkResult<ProductInventoryDto> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.inventoryApiService().updateReorderThreshold(
                sku,
                UpdateReorderThresholdRequestDto(threshold)
            )
        }
    }

    suspend fun deleteInventoryBySku(sku: String): NetworkResult<Unit> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiCall(networkModule.moshi) {
            networkModule.inventoryApiService().deleteInventoryBySku(sku)
        }
    }

    suspend fun fetchBatches(): NetworkResult<List<BatchDto>> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) { networkModule.inventoryApiService().getBatches() }
    }

    suspend fun createBatch(dto: BatchDto): NetworkResult<BatchDto> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) { networkModule.inventoryApiService().createBatch(dto) }
    }

    suspend fun adjustBatchStock(batchId: Long, newQuantity: Int, reason: String): NetworkResult<BatchDto> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.inventoryApiService().adjustBatchStock(
                batchId,
                AdjustBatchStockRequestDto(newQuantity, reason)
            )
        }
    }

    // 9. Notifications
    suspend fun fetchNotifications(): NetworkResult<List<NotificationDto>> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) { networkModule.notificationApiService().getNotifications() }
    }

    suspend fun markNotificationRead(id: Long): NetworkResult<Unit> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiCall(networkModule.moshi) { networkModule.notificationApiService().markNotificationRead(id) }
    }

    suspend fun markAllNotificationsRead(): NetworkResult<Unit> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiCall(networkModule.moshi) { networkModule.notificationApiService().markAllNotificationsRead() }
    }

    // 10. Drug Recalls
    suspend fun fetchRecalls(): NetworkResult<List<RecallDto>> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) { networkModule.inventoryApiService().getRecalls() }
    }

    suspend fun createRecall(request: CreateRecallRequestDto): NetworkResult<RecallDto> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) { networkModule.inventoryApiService().createRecall(request) }
    }

    // 11. Returns & Credit Notes
    suspend fun fetchReturns(): NetworkResult<List<ReturnDto>> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) { networkModule.orderApiService().getReturns() }
    }

    suspend fun createReturn(request: CreateReturnRequestDto): NetworkResult<ReturnDto> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) { networkModule.orderApiService().createReturn(request) }
    }

    suspend fun decideReturn(returnId: Long, isApproved: Boolean, decisionNotes: String): NetworkResult<Unit> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiCall(networkModule.moshi) {
            networkModule.orderApiService().decideReturn(returnId, ReturnDecisionRequestDto(isApproved, decisionNotes))
        }
    }

    suspend fun fetchCreditNotes(): NetworkResult<List<CreditNoteDto>> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) { networkModule.orderApiService().getCreditNotes() }
    }

    // 12. Credit Ledger
    suspend fun fetchLedgerEntries(counterpartyUserId: Long? = null): NetworkResult<List<CreditLedgerEntryDto>> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.orderApiService().getLedgerEntries(counterpartyUserId)
        }
    }

    suspend fun fetchLedgerBalance(counterpartyUserId: Long): NetworkResult<LedgerBalanceDto> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.orderApiService().getLedgerBalance(counterpartyUserId)
        }
    }

    suspend fun recordLedgerPayment(buyerId: Long, amount: Double, notes: String): NetworkResult<Unit> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiCall(networkModule.moshi) {
            networkModule.orderApiService().recordLedgerPayment(RecordLedgerPaymentRequestDto(buyerId, amount, notes))
        }
    }

    // 13 & 14. Trust Score, Shortage Alerts & Dashboard Analytics
    suspend fun fetchSupplierTrustScore(supplierId: Long): NetworkResult<TrustScoreDto> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.supplierApiService().getSupplierTrustScore(supplierId)
        }
    }

    suspend fun fetchTrustScoreComparison(): NetworkResult<List<TrustScoreDto>> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.supplierApiService().getSupplierTrustScoreComparison()
        }
    }

    suspend fun fetchShortageAlerts(): NetworkResult<List<ShortageAlertDto>> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.inventoryApiService().getShortageAlerts()
        }
    }

    suspend fun fetchDashboardAnalytics(): NetworkResult<DashboardStatsDto> {
        if (!networkModule.isBackendConfigured()) return notConfiguredError()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.supplierApiService().getDashboardAnalytics()
        }
    }
}
