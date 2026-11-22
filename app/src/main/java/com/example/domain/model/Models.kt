package com.example.domain.model

enum class UserRole(val displayName: String) {
    ADMIN("Administrator"),
    MANUFACTURER("Manufacturer"),
    PHARMA_COMPANY("Pharmaceutical Company / Brand"),
    STOCKIST("Stockist / Distributor");

    val isSupplier: Boolean
        get() = this == MANUFACTURER || this == PHARMA_COMPANY

    val isBuyer: Boolean
        get() = this == STOCKIST

    /**
     * Live Pipeline is restricted to authorized Manufacturer and Brand accounts (not Stockists).
     */
    val canAccessLivePipeline: Boolean
        get() = this == MANUFACTURER || this == PHARMA_COMPANY

    /**
     * Real-Time Inventory is intended exclusively for authorized Brand (PHARMA_COMPANY)
     * and Manufacturer accounts — strictly restricted for Stockist accounts.
     */
    val canAccessRealTimeInventory: Boolean
        get() = this == MANUFACTURER || this == PHARMA_COMPANY

    companion object {
        fun fromString(raw: String?): UserRole {
            if (raw.isNullOrBlank()) return STOCKIST
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: STOCKIST
        }
    }
}

enum class AccountStatus {
    ACTIVE,
    SUSPENDED
}

enum class DosageForm(val displayName: String) {
    TABLET("Tablet"),
    CAPSULE("Capsule"),
    SYRUP("Syrup"),
    INJECTION("Injection"),
    OINTMENT("Ointment"),
    DROPS("Drops"),
    OTHER("Other")
}

enum class StorageCondition(val label: String, val badgeColorHex: Long) {
    ROOM_TEMPERATURE("Room Temp (15–25°C)", 0xFF4A7C59),
    COOL_8_25("Cool (8–25°C)", 0xFF0077B6),
    REFRIGERATED_2_8("Refrigerated (2–8°C)", 0xFF023E8A),
    FROZEN("Frozen (Below 0°C)", 0xFF5A189A),
    PROTECT_FROM_LIGHT("Protect from Light", 0xFFCA6702)
}

enum class SchemeType(val displayName: String) {
    NONE("No Scheme"),
    BUY_X_GET_Y_FREE("Buy X Get Y Free"),
    PERCENT_DISCOUNT_ABOVE_QTY("Percent Discount Above Qty")
}

enum class OrderStatus(val displayName: String) {
    PLACED("Placed"),
    ACCEPTED("Accepted"),
    PACKED("Packed"),
    DISPATCHED("Dispatched"),
    DELIVERED("Delivered"),
    CANCELLED("Cancelled"),
    PARTIALLY_FULFILLED("Partially Fulfilled")
}

enum class ReturnReason(val displayName: String) {
    EXPIRED("Expired Stock"),
    DAMAGED("Damaged Goods"),
    NEAR_EXPIRY("Near Expiry"),
    WRONG_ITEM("Wrong Item Shipped"),
    RECALL("Batch Recall"),
    OTHER("Other")
}

enum class ReturnStatus(val displayName: String) {
    PENDING("Pending Approval"),
    APPROVED("Approved"),
    REJECTED("Rejected")
}

enum class ShortageRiskLevel(val label: String) {
    NONE("Normal Supply"),
    LOW("Low Risk"),
    MEDIUM("Moderate Risk"),
    HIGH("Critical Shortage Risk")
}

enum class RecallSeverity(val displayName: String) {
    CRITICAL_CLASS_1("Class I (Critical - Serious Risk)"),
    MAJOR_CLASS_2("Class II (Major - Temporary Risk)"),
    MINOR_CLASS_3("Class III (Minor - Technical Defect)")
}

enum class NotificationType {
    ORDER_STATUS_CHANGED,
    NEW_ORDER_RECEIVED,
    EXPIRY_ALERT,
    RECALL_ALERT,
    RETURN_UPDATE,
    SHORTAGE_RISK
}

data class AuthUser(
    val id: Long,
    val fullName: String,
    val businessName: String,
    val email: String,
    val phone: String,
    val gstin: String?,
    val address: String,
    val role: UserRole,
    val status: AccountStatus,
    val emailVerified: Boolean = false,
    val phoneVerified: Boolean = false,
    val profileBio: String? = null
)

enum class ContactVerificationChannel(val apiValue: String, val label: String) {
    EMAIL("EMAIL", "Email"),
    PHONE("PHONE", "Phone")
}

data class UserAddress(
    val id: Long = 0L,
    val userId: Long = 0L,
    val addressName: String,
    val contactPerson: String,
    val phone: String,
    val addressLine1: String,
    val addressLine2: String = "",
    val city: String,
    val state: String,
    val postalCode: String,
    val country: String = "India",
    val isDefault: Boolean = false,
    val updatedAtEpochMillis: Long = System.currentTimeMillis()
) {
    fun formattedSingleLine(): String = listOfNotNull(
        addressLine1.trim().takeIf { it.isNotEmpty() },
        addressLine2.trim().takeIf { it.isNotEmpty() },
        city.trim().takeIf { it.isNotEmpty() },
        state.trim().takeIf { it.isNotEmpty() },
        postalCode.trim().takeIf { it.isNotEmpty() },
        country.trim().takeIf { it.isNotEmpty() }
    ).joinToString(", ")
}

data class LegalDocument(
    val id: String,
    val title: String,
    val subtitle: String,
    val slug: String,
    val configuredUrl: String? = null,
    val versionLabel: String? = null,
    val contentSummary: List<String> = emptyList()
)

data class ProductDetail(
    val id: Long,
    val name: String,
    val brand: String,
    val saltComposition: String,
    val strength: String,
    val dosageForm: DosageForm,
    val therapeuticCategory: String,
    val manufacturerName: String,
    val packSize: String,
    val hsnCode: String,
    val gstRatePercent: Double,
    val storageCondition: StorageCondition,
    val isRx: Boolean,
    val isActive: Boolean,
    val createdBy: Long,
    val minOfferPrice: Double? = null,
    val activeOfferCount: Int = 0,
    val minMrp: Double? = null,
    val marginPercent: Int? = null,
    val bestOfferId: Long? = null
)

data class OfferWithSupplier(
    val offerId: Long,
    val productId: Long,
    val supplierId: Long,
    val supplierName: String,
    val supplierBusinessName: String,
    val unitPricePtr: Double,
    val mrp: Double,
    val moq: Int,
    val availableQuantity: Int,
    val schemeType: SchemeType,
    val schemeBuyQty: Int,
    val schemeFreeQty: Int,
    val schemeDiscountPercent: Double,
    val schemeMinQtyForDiscount: Int,
    val validTillEpochMillis: Long,
    val isActive: Boolean,
    val lastUpdatedEpochMillis: Long,
    val trustScore: Int? = null,
    val hasSufficientTrustData: Boolean = false,
    val marginPercent: Double = if (mrp > 0) ((mrp - unitPricePtr) / mrp) * 100.0 else 0.0
)

data class EffectivePriceCalculation(
    val regularQuantity: Int,
    val freeQuantity: Int,
    val totalReceivedQuantity: Int,
    val grossAmount: Double,
    val discountAmount: Double,
    val netAmount: Double,
    val effectiveUnitPrice: Double,
    val gstAmount: Double,
    val totalWithGst: Double,
    val savingsAmount: Double,
    val savingsPercent: Double
)

data class OptimizationResult(
    val isExact: Boolean,
    val currentTotal: Double,
    val optimizedTotal: Double,
    val savingsRupees: Double,
    val savingsPercent: Double,
    val lines: List<OptimizedLineItem>,
    val whySuggestions: List<String>,
    val assumptions: List<String>
)

data class OptimizedLineItem(
    val productId: Long,
    val productName: String,
    val brand: String,
    val originalSupplierId: Long,
    val originalSupplierName: String,
    val originalQuantity: Int,
    val originalEffectivePrice: Double,
    val suggestedSupplierId: Long,
    val suggestedSupplierName: String,
    val suggestedQuantity: Int,
    val suggestedFreeQty: Int,
    val suggestedUnitPrice: Double,
    val suggestedEffectivePrice: Double,
    val schemeApplied: String,
    val trustScore: Int?,
    val moqAdjusted: Boolean,
    val reason: String
)

data class TrustScoreBreakdown(
    val supplierId: Long,
    val supplierName: String,
    val overallScore: Int,
    val hasSufficientData: Boolean,
    val completedOrdersCount: Int,
    val fulfilmentRate: Double,
    val onTimeDeliveryRate: Double,
    val cancellationRate: Double,
    val discrepancyRate: Double,
    val returnRate: Double,
    val fulfilmentScoreWeight: Double,
    val onTimeScoreWeight: Double,
    val cancellationScoreWeight: Double,
    val discrepancyScoreWeight: Double,
    val returnScoreWeight: Double,
    val lastCalculatedEpochMillis: Long
)

data class ShortageEvaluationResult(
    val productId: Long,
    val productName: String,
    val brand: String,
    val saltComposition: String,
    val riskLevel: ShortageRiskLevel,
    val triggeredReasons: List<String>,
    val availableSuppliersCount: Int,
    val totalAvailableQuantity: Int,
    val avgLeadTimeDays: Double,
    val lastUpdatedEpochMillis: Long
)

data class ParsedOcrItem(
    val rawText: String,
    var medicineName: String,
    var strength: String,
    var quantity: Int,
    var matchedProductId: Long? = null,
    var matchedProductName: String? = null,
    var matchConfidence: Double = 0.0,
    var isConfirmed: Boolean = false
)

enum class StockLevelStatus(val label: String, val badgeColorHex: Long) {
    OUT_OF_STOCK("Out of Stock", 0xFFDC2626),
    CRITICAL_LOW("Critical Low", 0xFFEA580C),
    LOW_STOCK("Low Stock", 0xFFD97706),
    HEALTHY("In Stock", 0xFF16A34A)
}

enum class ExpiryRiskStatus(val label: String, val badgeColorHex: Long) {
    EXPIRED("Expired", 0xFFB91C1C),
    EXPIRING_CRITICAL("≤ 30 Days", 0xFFDC2626),
    EXPIRING_WARNING("≤ 90 Days", 0xFFD97706),
    HEALTHY("> 90 Days", 0xFF16A34A)
}

data class InventoryStockItem(
    val id: Long,
    val productId: Long,
    val productName: String,
    val brand: String,
    val saltComposition: String,
    val dosageForm: String,
    val therapeuticCategory: String,
    val batchNumber: String,
    val remainingQuantity: Int,
    val initialQuantity: Int,
    val reorderThreshold: Int = 20,
    val stockStatus: StockLevelStatus,
    val mfgDateEpochMillis: Long? = null,
    val expiryDateEpochMillis: Long,
    val daysUntilExpiry: Long,
    val expiryStatus: ExpiryRiskStatus,
    val storageCondition: StorageCondition,
    val mrp: Double,
    val ptr: Double,
    val totalValuation: Double,
    val isRecalled: Boolean = false,
    val recallReason: String? = null,
    val criticalFlags: List<String> = emptyList(),
    val supplierId: Long = 0L
) {
    val isCritical: Boolean
        get() = stockStatus == StockLevelStatus.OUT_OF_STOCK ||
                stockStatus == StockLevelStatus.CRITICAL_LOW ||
                expiryStatus == ExpiryRiskStatus.EXPIRED ||
                expiryStatus == ExpiryRiskStatus.EXPIRING_CRITICAL ||
                isRecalled

    val stockRatio: Float
        get() = if (initialQuantity > 0) (remainingQuantity.toFloat() / initialQuantity.toFloat()).coerceIn(0f, 1f) else 0f
}

data class InventoryDashboardSummary(
    val totalStockUnits: Int = 0,
    val totalInventoryValue: Double = 0.0,
    val totalSkusCount: Int = 0,
    val criticalItemsCount: Int = 0,
    val outOfStockCount: Int = 0,
    val lowStockCount: Int = 0,
    val nearExpiryCount: Int = 0,
    val recalledCount: Int = 0,
    val healthyCount: Int = 0
)

data class TradeMarginBreakdown(
    val marginPercent: Double?,
    val marginAmountPerUnit: Double?,
    val effectiveUnitCost: Double?,
    val unitPricePtr: Double?,
    val mrp: Double?,
    val netUnitPriceAfterScheme: Double?,
    val gstPerUnit: Double?,
    val gstRatePercent: Double,
    val deliveryCostApplied: Double?,
    val deliveryPerUnit: Double?,
    val freeDeliveryThreshold: Double?,
    val freeQuantityBonus: Int = 0,
    val evaluationQuantity: Int = 1,
    val hasAdditionalCostData: Boolean = false,
    val missingFields: List<String> = emptyList(),
    val formulaSummary: String = "Trade Margin % = ((MRP - Effective Unit Cost) / MRP) x 100"
)

data class VerifiedStockInfo(
    val verifiedAvailableUnits: Int? = null,
    val verifiedBatchCount: Int = 0,
    val verifiedInventorySkuCount: Int = 0,
    val hasVerifiedStockRecords: Boolean = false
)

data class SupplierQuotationItem(
    val offer: OfferWithSupplier,
    val effectiveUnitCost: Double?,
    val tradeMargin: TradeMarginBreakdown,
    val deliveryCharge: Double?,
    val freeDeliveryThreshold: Double?,
    val averageLeadTimeDays: Double?,
    val quotedStock: Int,
    val verifiedStock: Int?,
    val hasVerifiedStockRecord: Boolean,
    val isExpired: Boolean,
    val isEligibleForPurchase: Boolean,
    val unavailabilityReason: String?
)

data class SupplierQuotationSummary(
    val minMoq: Int?,
    val maxMoq: Int?,
    val lowestUnitPricePtr: Double?,
    val lowestEffectiveUnitCost: Double?,
    val availableSuppliersCount: Int,
    val totalOffersCount: Int,
    val totalQuotedAvailableStock: Int,
    val verifiedAvailableStock: Int?,
    val hasVerifiedStockRecords: Boolean,
    val hasEligibleOffers: Boolean,
    val bestOfferId: Long?,
    val missingFields: List<String> = emptyList()
)

data class AlternativeBrandQuotation(
    val product: com.example.data.local.entity.ProductEntity,
    val manufacturerName: String,
    val unitPricePtr: Double?,
    val effectiveUnitCost: Double?,
    val mrp: Double?,
    val moq: Int?,
    val supplierName: String?,
    val supplierRating: Int?,
    val hasSufficientRatingData: Boolean,
    val gstRatePercent: Double,
    val deliveryCharge: Double?,
    val freeDeliveryThreshold: Double?,
    val tradeMargin: TradeMarginBreakdown,
    val availableSuppliersCount: Int,
    val totalQuotedStock: Int,
    val hasEligibleOffer: Boolean,
    val missingFields: List<String> = emptyList()
)

data class TaxInvoiceParty(
    val legalName: String?,
    val contactName: String?,
    val address: String?,
    val shippingAddress: String? = null,
    val gstin: String?,
    val phone: String?,
    val email: String?
)

data class TaxInvoiceLineItem(
    val productName: String,
    val brand: String,
    val packSize: String,
    val hsnCode: String,
    val batchNumber: String?,
    val quantity: Int,
    val freeQuantity: Int,
    val unitPricePtr: Double,
    val effectiveUnitPrice: Double,
    val taxableValue: Double,
    val gstRatePercent: Double,
    val gstAmount: Double?,
    val cgstAmount: Double?,
    val sgstAmount: Double?,
    val igstAmount: Double?,
    val lineTotal: Double
)

data class TaxInvoiceData(
    val orderId: Long,
    val orderNumber: String,
    val orderStatus: OrderStatus,
    val orderDateEpochMillis: Long,
    val invoiceNumber: String?,
    val invoiceDateEpochMillis: Long?,
    val isEligible: Boolean,
    val eligibilityReason: String?,
    val isBackendAuthoritative: Boolean = false,
    val seller: TaxInvoiceParty,
    val buyer: TaxInvoiceParty,
    val placeOfSupply: String?,
    val gstTaxType: String?, // "CGST + SGST", "IGST", or null when state codes unavailable
    val items: List<TaxInvoiceLineItem>,
    val subtotalTaxable: Double,
    val cgstTotal: Double?,
    val sgstTotal: Double?,
    val igstTotal: Double?,
    val totalGstAmount: Double?,
    val deliveryCharge: Double,
    val grandTotal: Double,
    val ewayBillNumber: String? = null,
    val vehicleNumber: String? = null,
    val transporterName: String? = null,
    val missingFields: List<String> = emptyList()
) {
    val hasCompleteTaxMetadata: Boolean
        get() = isEligible && !invoiceNumber.isNullOrBlank() && missingFields.isEmpty()
}


