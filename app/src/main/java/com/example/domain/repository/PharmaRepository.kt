package com.example.domain.repository

import com.example.data.local.entity.BatchEntity
import com.example.data.local.entity.CreditLedgerEntryEntity
import com.example.data.local.entity.CreditNoteEntity
import com.example.data.local.entity.NotificationEntity
import com.example.data.local.entity.OrderEntity
import com.example.data.local.entity.OrderItemEntity
import com.example.data.local.entity.OrderStatusHistoryEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.ProductInventoryEntity
import com.example.data.local.entity.ProofOfDeliveryEntity
import com.example.data.local.entity.RecallEntity
import com.example.data.local.entity.ReturnEntity
import com.example.data.local.entity.SupplierOfferEntity
import com.example.data.local.entity.SupplierSettingsEntity
import com.example.data.local.entity.UserEntity
import com.example.domain.model.AuthUser
import com.example.domain.model.EffectivePriceCalculation
import com.example.domain.model.InventoryDashboardSummary
import com.example.domain.model.InventoryStockItem
import com.example.domain.model.OfferWithSupplier
import com.example.domain.model.OptimizationResult
import com.example.domain.model.ParsedOcrItem
import com.example.domain.model.ProductDetail
import com.example.domain.model.ReturnReason
import com.example.domain.model.ShortageEvaluationResult
import com.example.domain.model.TrustScoreBreakdown
import com.example.domain.model.UserRole
import kotlinx.coroutines.flow.Flow

interface PharmaRepository {
    // Auth & Users
    suspend fun postApiAuthLogin(email: String, password: String, expectedRole: UserRole? = null): Result<AuthUser>
    suspend fun postApiAuthGoogle(email: String, displayName: String, role: UserRole): Result<AuthUser>
    suspend fun postApiAuthRegister(
        fullName: String,
        businessName: String,
        email: String,
        phone: String,
        gstin: String?,
        address: String,
        password: String,
        role: UserRole
    ): Result<AuthUser>
    fun getApiUsersMeFlow(): Flow<AuthUser?>
    suspend fun getApiUsersMe(): AuthUser?
    suspend fun logout()
    fun getApiUsersFlow(): Flow<List<UserEntity>>
    suspend fun patchApiUserStatus(userId: Long, status: String): Result<Unit>
    suspend fun getFirstUserRegistrationCheck(): Boolean
    suspend fun ensureDefaultManufacturerUser(): UserEntity
    suspend fun createManufacturerTestingUser(
        fullName: String = "Dr. Vikram Malhotra (VP Manufacturing)",
        businessName: String = "Bharat Bio-Pharma Formulations Pvt. Ltd.",
        email: String = "manufacturer@pharmaos.com",
        phone: String = "9876543220",
        password: String = "Password@123",
        gstin: String = "27AAACB1234F1Z9",
        address: String = "Plant 1, Plot 88, MIDC Tarapur Industrial Area, Boisar, Palghar, Maharashtra 401506"
    ): UserEntity
    suspend fun saveComplianceVerification(
        userId: Long,
        gstin: String,
        drugLicenseNumber: String,
        drugLicenseIssueDate: String,
        drugLicenseExpiryDate: String,
        drugLicenseDocUri: String?,
        whoGmpCertificateNumber: String?,
        whoGmpIssueDate: String?,
        whoGmpExpiryDate: String?,
        whoGmpDocUri: String?,
        isWhoGmpApplicable: Boolean
    ): Result<Unit>
    fun getComplianceVerificationFlow(userId: Long): Flow<com.example.data.local.entity.ComplianceVerificationEntity?>

    // Products
    suspend fun ensureDefaultCatalogueSeeded()
    fun getApiProductsFlow(): Flow<List<ProductDetail>>
    suspend fun getApiProducts(): List<ProductDetail>
    suspend fun getApiProductById(id: Long): ProductDetail?
    fun getApiProductByIdFlow(id: Long): Flow<ProductDetail?>
    suspend fun postApiProducts(product: ProductEntity): Result<Long>
    suspend fun putApiProducts(product: ProductEntity): Result<Unit>
    suspend fun patchApiProductDeactivate(id: Long, isActive: Boolean): Result<Unit>
    fun getApiProductAlternativeBrandsFlow(id: Long): Flow<List<ProductEntity>>

    // Offers
    fun getApiProductOffersFlow(productId: Long): Flow<List<OfferWithSupplier>>
    suspend fun getApiProductOffers(productId: Long): List<OfferWithSupplier>
    fun getApiSupplierOffersFlow(supplierId: Long): Flow<List<OfferWithSupplier>>
    suspend fun postApiSupplierOffers(offer: SupplierOfferEntity): Result<Long>
    suspend fun putApiSupplierOffers(offer: SupplierOfferEntity): Result<Unit>
    suspend fun patchApiSupplierOfferDeactivate(offerId: Long, isActive: Boolean): Result<Unit>

    // Basket
    fun getApiBasketFlow(): Flow<BasketWithDetails>
    suspend fun getApiBasket(): BasketWithDetails
    suspend fun postApiBasketItems(productId: Long, offerId: Long, quantity: Int): Result<Unit>
    suspend fun putApiBasketItemQuantity(itemId: Long, quantity: Int): Result<Unit>
    suspend fun deleteApiBasketItem(itemId: Long): Result<Unit>
    suspend fun deleteApiBasketClear(): Result<Unit>
    suspend fun postApiBasketSaveNamed(name: String): Result<Long>
    fun getApiSavedBasketsFlow(): Flow<List<NamedBasketSummary>>
    suspend fun postApiBasketLoadSaved(savedBasketId: Long): Result<BasketLoadReport>

    // Optimization
    suspend fun postApiBasketOptimize(preferReliableSuppliers: Boolean): OptimizationResult
    suspend fun postApiBasketApplyOptimization(result: OptimizationResult): Result<Unit>

    // Orders
    suspend fun ensureDefaultOrdersSeeded()
    suspend fun postApiOrders(deliveryAddress: String, notes: String): Result<List<Long>>
    fun getApiOrdersFlow(): Flow<List<OrderEntity>>
    suspend fun getApiOrders(): List<OrderEntity>
    fun getApiSupplierOrdersFlow(supplierId: Long): Flow<List<OrderEntity>>
    suspend fun getApiOrderById(id: Long): OrderDetailsWithHistory?
    fun getApiOrderByIdFlow(id: Long): Flow<OrderDetailsWithHistory?>
    suspend fun patchApiOrderStatus(
        orderId: Long,
        newStatus: String,
        note: String,
        dispatchEpochMillis: Long? = null,
        expectedDeliveryEpochMillis: Long? = null,
        ewayBillNumber: String? = null,
        vehicleNumber: String? = null,
        transporterName: String? = null
    ): Result<Unit>
    suspend fun patchApiOrderItemFulfillment(
        orderItemId: Long,
        fulfilledQty: Int,
        batchNumber: String?,
        expiryDateEpochMillis: Long?
    ): Result<Unit>
    suspend fun postApiOrderProofOfDelivery(
        orderId: Long,
        receivedByName: String,
        notes: String,
        photoUri: String?,
        shortQuantity: Int,
        damagedQuantity: Int
    ): Result<Unit>
    suspend fun postApiOrderReorder(pastOrderId: Long): Result<Unit>
    fun getOrderHistoryTransactionsFlow(): Flow<List<com.example.ui.components.OrderTransactionUiModel>>

    // Batches & Real-Time Inventory
    fun getApiBatchesFlow(): Flow<List<BatchWithProduct>>
    suspend fun getApiBatches(): List<BatchWithProduct>
    suspend fun postApiBatches(batch: BatchEntity): Result<Long>
    fun getRealTimeInventoryFlow(): Flow<List<InventoryStockItem>>
    fun getInventorySummaryFlow(): Flow<InventoryDashboardSummary>
    suspend fun adjustBatchStock(batchId: Long, newQuantity: Int, reason: String): Result<Unit>
    suspend fun quickRestockToBasket(productId: Long, quantity: Int = 50): Result<Unit>

    // Product Inventory Level Management (Room)
    fun getProductInventoryFlow(): Flow<List<ProductInventoryEntity>>
    fun getLowStockProductInventoryFlow(): Flow<List<ProductInventoryEntity>>
    suspend fun getProductInventoryBySku(sku: String): ProductInventoryEntity?
    suspend fun insertOrUpdateProductInventory(inventory: ProductInventoryEntity): Long
    suspend fun updateProductInventoryStock(sku: String, newStock: Int): Result<Unit>
    suspend fun updateProductInventoryReorderThreshold(sku: String, newThreshold: Int): Result<Unit>

    // Notifications
    fun getApiNotificationsFlow(): Flow<List<NotificationEntity>>
    fun getApiNotificationsUnreadCountFlow(): Flow<Int>
    suspend fun patchApiNotificationRead(id: Long): Result<Unit>
    suspend fun patchApiNotificationsReadAll(): Result<Unit>

    // Intelligence: Trust Score & Shortage
    suspend fun getApiSuppliersTrustScore(supplierId: Long): TrustScoreBreakdown
    fun getApiSuppliersTrustScoreFlow(supplierId: Long): Flow<TrustScoreBreakdown?>
    fun getApiSupplierTrustScoreComparisonFlow(): Flow<List<TrustScoreBreakdown>>
    fun getApiShortageAlertsFlow(): Flow<List<ShortageEvaluationResult>>
    suspend fun evaluateAllShortageRisks(): List<ShortageEvaluationResult>

    // Recalls
    fun getApiRecallsFlow(): Flow<List<RecallWithAffectedStock>>
    suspend fun postApiRecalls(
        productId: Long,
        batchNumbersCsv: String,
        reason: String,
        severity: String
    ): Result<Long>

    // Returns & Credit Notes
    fun getApiReturnsFlow(): Flow<List<ReturnWithDetails>>
    suspend fun postApiReturns(
        orderId: Long,
        orderItemId: Long,
        quantity: Int,
        reason: ReturnReason,
        batchNumber: String
    ): Result<Long>
    suspend fun patchApiReturnStatus(returnId: Long, isApproved: Boolean, decisionNotes: String): Result<Unit>
    fun getApiCreditNotesFlow(): Flow<List<CreditNoteEntity>>

    // Credit Ledger
    fun getApiLedgerEntriesFlow(counterpartyUserId: Long?): Flow<List<CreditLedgerEntryEntity>>
    suspend fun getApiLedgerBalance(counterpartyUserId: Long): Double
    suspend fun postApiLedgerPayment(buyerId: Long, amount: Double, notes: String): Result<Unit>

    // Settings
    suspend fun getApiSupplierSettings(supplierId: Long): SupplierSettingsEntity
    suspend fun putApiSupplierSettings(settings: SupplierSettingsEntity): Result<Unit>

    // Dashboard KPIs
    suspend fun getDashboardStats(): DashboardKpis
}

data class BasketWithDetails(
    val basketId: Long,
    val items: List<BasketItemDetail>,
    val subtotal: Double,
    val totalGst: Double,
    val totalDeliveryCharges: Double,
    val grandTotal: Double,
    val totalSavings: Double,
    val supplierSubtotals: Map<Long, SupplierSubtotal>
)

data class BasketItemDetail(
    val itemId: Long,
    val productId: Long,
    val productName: String,
    val brand: String,
    val saltComposition: String,
    val packSize: String,
    val hsnCode: String,
    val gstRatePercent: Double,
    val offerId: Long,
    val supplierId: Long,
    val supplierName: String,
    val unitPricePtr: Double,
    val mrp: Double,
    val moq: Int,
    val availableQuantity: Int,
    val quantity: Int,
    val pricing: EffectivePriceCalculation,
    val schemeDescription: String,
    val trustScore: Int?,
    val hasSufficientTrustData: Boolean
)

data class SupplierSubtotal(
    val supplierId: Long,
    val supplierName: String,
    val subtotal: Double,
    val gstAmount: Double,
    val deliveryCharge: Double,
    val freeDeliveryThreshold: Double,
    val total: Double,
    val isFreeDeliveryMet: Boolean
)

data class NamedBasketSummary(
    val id: Long,
    val name: String,
    val itemCount: Int,
    val updatedAtEpochMillis: Long
)

data class BasketLoadReport(
    val itemsLoadedCount: Int,
    val priceChangeWarnings: List<String>,
    val unavailableWarnings: List<String>
)

data class OrderDetailsWithHistory(
    val order: OrderEntity,
    val buyer: UserEntity?,
    val supplier: UserEntity?,
    val items: List<OrderItemEntity>,
    val history: List<OrderStatusHistoryEntity>,
    val pod: ProofOfDeliveryEntity?
)

data class BatchWithProduct(
    val batch: BatchEntity,
    val productName: String,
    val brand: String,
    val saltComposition: String,
    val daysUntilExpiry: Long
)

data class RecallWithAffectedStock(
    val recall: RecallEntity,
    val affectedBatches: List<BatchEntity>,
    val affectedStockUnits: Int
)

data class ReturnWithDetails(
    val returnRecord: ReturnEntity,
    val productName: String,
    val orderNumber: String,
    val creditNote: CreditNoteEntity?
)

data class DashboardKpis(
    val role: UserRole,
    // Buyer
    val openOrdersCount: Int = 0,
    val basketItemsCount: Int = 0,
    val nearExpiryBatchesCount: Int = 0,
    val shortageAlertsCount: Int = 0,
    val ledgerBalance: Double = 0.0,
    // Supplier
    val activeOffersCount: Int = 0,
    val pendingOrdersCount: Int = 0,
    val lowStockCount: Int = 0,
    val trustScore: Int? = null,
    // Admin
    val totalUsersCount: Int = 0,
    val manufacturersCount: Int = 0,
    val pharmaCompaniesCount: Int = 0,
    val stockistsCount: Int = 0,
    val totalProductsCount: Int = 0,
    val totalOrdersCount: Int = 0,
    val activeRecallsCount: Int = 0
)
