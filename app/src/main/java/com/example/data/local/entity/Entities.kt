package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [Index(value = ["email"], unique = true)]
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fullName: String,
    val businessName: String,
    val email: String,
    val phone: String,
    val gstin: String?,
    val address: String,
    val passwordHash: String,
    val passwordSalt: String,
    val role: String,
    val status: String,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "products",
    indices = [
        Index(value = ["name"]),
        Index(value = ["brand"]),
        Index(value = ["saltComposition"]),
        Index(value = ["therapeuticCategory"])
    ]
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brand: String,
    val saltComposition: String,
    val strength: String,
    val dosageForm: String,
    val therapeuticCategory: String,
    val manufacturerName: String,
    val packSize: String,
    val hsnCode: String,
    val gstRatePercent: Double,
    val storageCondition: String,
    val isRx: Boolean,
    val isActive: Boolean = true,
    val createdBy: Long,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "supplier_offers",
    indices = [
        Index(value = ["productId"]),
        Index(value = ["supplierId"])
    ]
)
data class SupplierOfferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val supplierId: Long,
    val unitPricePtr: Double,
    val mrp: Double,
    val moq: Int,
    val availableQuantity: Int,
    val schemeType: String,
    val schemeBuyQty: Int = 0,
    val schemeFreeQty: Int = 0,
    val schemeDiscountPercent: Double = 0.0,
    val schemeMinQtyForDiscount: Int = 0,
    val validTillEpochMillis: Long,
    val isActive: Boolean = true,
    val lastUpdatedEpochMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "baskets",
    indices = [Index(value = ["buyerId"])]
)
data class BasketEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val buyerId: Long,
    val name: String? = null,
    val isSaved: Boolean = false,
    val updatedAtEpochMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "basket_items",
    indices = [
        Index(value = ["basketId"]),
        Index(value = ["productId"]),
        Index(value = ["offerId"])
    ]
)
data class BasketItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val basketId: Long,
    val productId: Long,
    val offerId: Long,
    val supplierId: Long,
    val quantity: Int,
    val addedAtEpochMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "orders",
    indices = [
        Index(value = ["buyerId"]),
        Index(value = ["supplierId"]),
        Index(value = ["orderNumber"], unique = true)
    ]
)
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderNumber: String,
    val buyerId: Long,
    val supplierId: Long,
    val status: String,
    val subtotal: Double,
    val gstAmount: Double,
    val deliveryCharge: Double,
    val totalAmount: Double,
    val deliveryAddress: String,
    val notes: String = "",
    val isEwayBillGenerated: Boolean = false,
    val ewayBillNumber: String? = null,
    val vehicleNumber: String? = null,
    val transporterName: String? = null,
    val expectedDeliveryEpochMillis: Long? = null,
    val dispatchEpochMillis: Long? = null,
    val deliveredEpochMillis: Long? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "order_items",
    indices = [Index(value = ["orderId"])]
)
data class OrderItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val productId: Long,
    val offerId: Long,
    val productName: String,
    val brand: String,
    val packSize: String,
    val hsnCode: String,
    val gstRatePercent: Double,
    val orderedQuantity: Int,
    val freeQuantity: Int = 0,
    val fulfilledQuantity: Int = 0,
    val unitPricePtr: Double,
    val effectivePrice: Double,
    val batchNumber: String? = null,
    val expiryDateEpochMillis: Long? = null
)

@Entity(
    tableName = "order_status_history",
    indices = [Index(value = ["orderId"])]
)
data class OrderStatusHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val status: String,
    val note: String = "",
    val changedAtEpochMillis: Long = System.currentTimeMillis(),
    val changedByUserId: Long
)

@Entity(
    tableName = "batches",
    indices = [
        Index(value = ["productId"]),
        Index(value = ["supplierId"]),
        Index(value = ["batchNumber"])
    ]
)
data class BatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val supplierId: Long,
    val batchNumber: String,
    val mfgDateEpochMillis: Long? = null,
    val expiryDateEpochMillis: Long,
    val initialQuantity: Int,
    val remainingQuantity: Int,
    val storageCondition: String,
    val mrp: Double,
    val ptr: Double,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "stock_records",
    indices = [Index(value = ["productId"])]
)
data class StockRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val supplierId: Long,
    val offerId: Long,
    val availableQuantity: Int,
    val recordedAtEpochMillis: Long = System.currentTimeMillis(),
    val reason: String
)

@Entity(
    tableName = "returns",
    indices = [
        Index(value = ["orderId"]),
        Index(value = ["buyerId"]),
        Index(value = ["supplierId"])
    ]
)
data class ReturnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val orderItemId: Long,
    val buyerId: Long,
    val supplierId: Long,
    val productId: Long,
    val batchNumber: String,
    val quantity: Int,
    val reason: String,
    val status: String,
    val requestedAtEpochMillis: Long = System.currentTimeMillis(),
    val decisionAtEpochMillis: Long? = null,
    val decisionNotes: String = ""
)

@Entity(
    tableName = "credit_notes",
    indices = [
        Index(value = ["creditNoteNumber"], unique = true),
        Index(value = ["returnId"]),
        Index(value = ["buyerId"]),
        Index(value = ["supplierId"])
    ]
)
data class CreditNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val creditNoteNumber: String,
    val returnId: Long,
    val orderId: Long,
    val buyerId: Long,
    val supplierId: Long,
    val amount: Double,
    val gstAmount: Double,
    val totalAmount: Double,
    val issuedAtEpochMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "credit_ledger",
    indices = [
        Index(value = ["buyerId"]),
        Index(value = ["supplierId"])
    ]
)
data class CreditLedgerEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val buyerId: Long,
    val supplierId: Long,
    val entryType: String, // DEBIT, CREDIT
    val amount: Double,
    val balanceAfter: Double,
    val referenceType: String, // ORDER, RETURN_CREDIT_NOTE, PAYMENT
    val referenceId: String,
    val description: String,
    val dateEpochMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "notifications",
    indices = [Index(value = ["recipientUserId"])]
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipientUserId: Long,
    val type: String,
    val title: String,
    val message: String,
    val referenceId: String? = null,
    val isRead: Boolean = false,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "supplier_performance",
    indices = [Index(value = ["supplierId"], unique = true)]
)
data class SupplierPerformanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val supplierId: Long,
    val overallScore: Int,
    val completedOrdersCount: Int,
    val fulfilmentRate: Double,
    val onTimeRate: Double,
    val cancellationRate: Double,
    val discrepancyRate: Double,
    val returnRate: Double,
    val calculatedAtEpochMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "recalls",
    indices = [Index(value = ["productId"])]
)
data class RecallEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val productName: String,
    val batchNumbersCsv: String,
    val reason: String,
    val severity: String,
    val createdBySupplierId: Long,
    val recallDateEpochMillis: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)

@Entity(
    tableName = "shortage_risks",
    indices = [Index(value = ["productId"], unique = true)]
)
data class ShortageRiskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val riskLevel: String,
    val triggeredReasonsCsv: String,
    val totalAvailableQuantity: Int,
    val availableSuppliersCount: Int,
    val avgLeadTimeDays: Double,
    val evaluatedAtEpochMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "proof_of_delivery",
    indices = [Index(value = ["orderId"], unique = true)]
)
data class ProofOfDeliveryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val receivedDateEpochMillis: Long = System.currentTimeMillis(),
    val receivedByName: String,
    val notes: String = "",
    val photoUri: String? = null,
    val shortQuantity: Int = 0,
    val damagedQuantity: Int = 0
)

@Entity(
    tableName = "supplier_settings",
    indices = [Index(value = ["supplierId"], unique = true)]
)
data class SupplierSettingsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val supplierId: Long,
    val deliveryCharge: Double = 0.0,
    val freeDeliveryThreshold: Double = 5000.0,
    val averageLeadTimeDays: Double = 3.0
)

@Entity(
    tableName = "compliance_verifications",
    indices = [Index(value = ["userId"], unique = true)]
)
data class ComplianceVerificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val gstin: String,
    val isGstinVerified: Boolean = true,
    val drugLicenseNumber: String,
    val drugLicenseIssueDate: String,
    val drugLicenseExpiryDate: String,
    val drugLicenseDocUri: String? = null,
    val whoGmpCertificateNumber: String? = null,
    val whoGmpIssueDate: String? = null,
    val whoGmpExpiryDate: String? = null,
    val whoGmpDocUri: String? = null,
    val isWhoGmpApplicable: Boolean = true,
    val status: String = "VERIFIED",
    val verifiedAtEpochMillis: Long = System.currentTimeMillis()
)
