package com.example.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// =========================================================================
// [PROPOSED — AWAITING BACKEND CONFIRMATION]
// Domain DTOs mapped for Spring Boot REST API & PostgreSQL JDBC schema.
// =========================================================================

// --- 1. USERS & COMPLIANCE ---

@JsonClass(generateAdapter = true)
data class UpdateUserStatusRequestDto(
    @param:Json(name = "status") val status: String
)

@JsonClass(generateAdapter = true)
data class UpdateUserProfileRequestDto(
    @param:Json(name = "fullName") val fullName: String,
    @param:Json(name = "businessName") val businessName: String,
    @param:Json(name = "email") val email: String,
    @param:Json(name = "phone") val phone: String,
    @param:Json(name = "gstin") val gstin: String? = null,
    @param:Json(name = "address") val address: String,
    @param:Json(name = "profileBio") val profileBio: String? = null
)

@JsonClass(generateAdapter = true)
data class ContactVerificationRequestDto(
    @param:Json(name = "channel") val channel: String,
    @param:Json(name = "value") val value: String
)

@JsonClass(generateAdapter = true)
data class ConfirmContactVerificationRequestDto(
    @param:Json(name = "channel") val channel: String,
    @param:Json(name = "value") val value: String,
    @param:Json(name = "code") val code: String
)

@JsonClass(generateAdapter = true)
data class ContactVerificationStatusDto(
    @param:Json(name = "channel") val channel: String,
    @param:Json(name = "verified") val verified: Boolean = false,
    @param:Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class UserAddressDto(
    @param:Json(name = "id") val id: Long = 0,
    @param:Json(name = "userId") val userId: Long = 0,
    @param:Json(name = "addressName") val addressName: String,
    @param:Json(name = "contactPerson") val contactPerson: String,
    @param:Json(name = "phone") val phone: String,
    @param:Json(name = "addressLine1") val addressLine1: String,
    @param:Json(name = "addressLine2") val addressLine2: String = "",
    @param:Json(name = "city") val city: String,
    @param:Json(name = "state") val state: String,
    @param:Json(name = "postalCode") val postalCode: String,
    @param:Json(name = "country") val country: String = "India",
    @param:Json(name = "isDefault") val isDefault: Boolean = false,
    @param:Json(name = "updatedAtEpochMillis") val updatedAtEpochMillis: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class LegalDocumentDto(
    @param:Json(name = "id") val id: String,
    @param:Json(name = "title") val title: String,
    @param:Json(name = "subtitle") val subtitle: String? = null,
    @param:Json(name = "slug") val slug: String? = null,
    @param:Json(name = "url") val url: String? = null,
    @param:Json(name = "version") val version: String? = null
)

@JsonClass(generateAdapter = true)
data class ComplianceVerificationDto(
    @param:Json(name = "id") val id: Long? = null,
    @param:Json(name = "userId") val userId: Long,
    @param:Json(name = "gstin") val gstin: String,
    @param:Json(name = "isGstinVerified") val isGstinVerified: Boolean = true,
    @param:Json(name = "drugLicenseNumber") val drugLicenseNumber: String,
    @param:Json(name = "drugLicenseIssueDate") val drugLicenseIssueDate: String,
    @param:Json(name = "drugLicenseExpiryDate") val drugLicenseExpiryDate: String,
    @param:Json(name = "drugLicenseDocUri") val drugLicenseDocUri: String? = null,
    @param:Json(name = "whoGmpCertificateNumber") val whoGmpCertificateNumber: String? = null,
    @param:Json(name = "whoGmpIssueDate") val whoGmpIssueDate: String? = null,
    @param:Json(name = "whoGmpExpiryDate") val whoGmpExpiryDate: String? = null,
    @param:Json(name = "whoGmpDocUri") val whoGmpDocUri: String? = null,
    @param:Json(name = "isWhoGmpApplicable") val isWhoGmpApplicable: Boolean = true,
    @param:Json(name = "status") val status: String = "VERIFIED",
    @param:Json(name = "verifiedAtEpochMillis") val verifiedAtEpochMillis: Long? = null
)

// --- 2. PRODUCTS ---

@JsonClass(generateAdapter = true)
data class ProductDto(
    @param:Json(name = "id") val id: Long = 0,
    @param:Json(name = "name") val name: String,
    @param:Json(name = "brand") val brand: String,
    @param:Json(name = "saltComposition") val saltComposition: String,
    @param:Json(name = "strength") val strength: String,
    @param:Json(name = "dosageForm") val dosageForm: String,
    @param:Json(name = "therapeuticCategory") val therapeuticCategory: String,
    @param:Json(name = "manufacturerName") val manufacturerName: String,
    @param:Json(name = "packSize") val packSize: String,
    @param:Json(name = "hsnCode") val hsnCode: String,
    @param:Json(name = "gstRatePercent") val gstRatePercent: Double,
    @param:Json(name = "storageCondition") val storageCondition: String,
    @param:Json(name = "isRx") val isRx: Boolean,
    @param:Json(name = "isActive") val isActive: Boolean = true,
    @param:Json(name = "createdBy") val createdBy: Long = 0L,
    @param:Json(name = "createdAtEpochMillis") val createdAtEpochMillis: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class UpdateProductStatusRequestDto(
    @param:Json(name = "isActive") val isActive: Boolean
)

// --- 3. SUPPLIER OFFERS & SETTINGS ---

@JsonClass(generateAdapter = true)
data class SupplierOfferDto(
    @param:Json(name = "id") val id: Long = 0,
    @param:Json(name = "productId") val productId: Long,
    @param:Json(name = "supplierId") val supplierId: Long,
    @param:Json(name = "supplierName") val supplierName: String? = null,
    @param:Json(name = "supplierBusinessName") val supplierBusinessName: String? = null,
    @param:Json(name = "unitPricePtr") val unitPricePtr: Double,
    @param:Json(name = "mrp") val mrp: Double,
    @param:Json(name = "moq") val moq: Int,
    @param:Json(name = "availableQuantity") val availableQuantity: Int,
    @param:Json(name = "schemeType") val schemeType: String,
    @param:Json(name = "schemeBuyQty") val schemeBuyQty: Int = 0,
    @param:Json(name = "schemeFreeQty") val schemeFreeQty: Int = 0,
    @param:Json(name = "schemeDiscountPercent") val schemeDiscountPercent: Double = 0.0,
    @param:Json(name = "schemeMinQtyForDiscount") val schemeMinQtyForDiscount: Int = 0,
    @param:Json(name = "validTillEpochMillis") val validTillEpochMillis: Long,
    @param:Json(name = "isActive") val isActive: Boolean = true,
    @param:Json(name = "lastUpdatedEpochMillis") val lastUpdatedEpochMillis: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class SupplierSettingsDto(
    @param:Json(name = "supplierId") val supplierId: Long,
    @param:Json(name = "deliveryCharge") val deliveryCharge: Double = 0.0,
    @param:Json(name = "freeDeliveryThreshold") val freeDeliveryThreshold: Double = 5000.0,
    @param:Json(name = "averageLeadTimeDays") val averageLeadTimeDays: Double = 3.0
)

// --- 4. BASKET & OPTIMIZATION ---

@JsonClass(generateAdapter = true)
data class AddBasketItemRequestDto(
    @param:Json(name = "productId") val productId: Long,
    @param:Json(name = "offerId") val offerId: Long,
    @param:Json(name = "quantity") val quantity: Int
)

@JsonClass(generateAdapter = true)
data class UpdateBasketQtyRequestDto(
    @param:Json(name = "quantity") val quantity: Int
)

@JsonClass(generateAdapter = true)
data class SaveNamedBasketRequestDto(
    @param:Json(name = "name") val name: String
)

@JsonClass(generateAdapter = true)
data class OptimizeBasketRequestDto(
    @param:Json(name = "preferReliableSuppliers") val preferReliableSuppliers: Boolean
)

@JsonClass(generateAdapter = true)
data class BasketItemDto(
    @param:Json(name = "itemId") val itemId: Long,
    @param:Json(name = "productId") val productId: Long,
    @param:Json(name = "productName") val productName: String,
    @param:Json(name = "brand") val brand: String,
    @param:Json(name = "saltComposition") val saltComposition: String,
    @param:Json(name = "packSize") val packSize: String,
    @param:Json(name = "hsnCode") val hsnCode: String,
    @param:Json(name = "gstRatePercent") val gstRatePercent: Double,
    @param:Json(name = "offerId") val offerId: Long,
    @param:Json(name = "supplierId") val supplierId: Long,
    @param:Json(name = "supplierName") val supplierName: String,
    @param:Json(name = "unitPricePtr") val unitPricePtr: Double,
    @param:Json(name = "mrp") val mrp: Double,
    @param:Json(name = "moq") val moq: Int,
    @param:Json(name = "availableQuantity") val availableQuantity: Int,
    @param:Json(name = "quantity") val quantity: Int,
    @param:Json(name = "effectiveUnitPrice") val effectiveUnitPrice: Double,
    @param:Json(name = "lineTotalWithGst") val lineTotalWithGst: Double,
    @param:Json(name = "schemeDescription") val schemeDescription: String = ""
)

@JsonClass(generateAdapter = true)
data class BasketDto(
    @param:Json(name = "basketId") val basketId: Long,
    @param:Json(name = "items") val items: List<BasketItemDto> = emptyList(),
    @param:Json(name = "subtotal") val subtotal: Double = 0.0,
    @param:Json(name = "totalGst") val totalGst: Double = 0.0,
    @param:Json(name = "totalDeliveryCharges") val totalDeliveryCharges: Double = 0.0,
    @param:Json(name = "grandTotal") val grandTotal: Double = 0.0,
    @param:Json(name = "totalSavings") val totalSavings: Double = 0.0
)

@JsonClass(generateAdapter = true)
data class NamedBasketDto(
    @param:Json(name = "id") val id: Long,
    @param:Json(name = "name") val name: String,
    @param:Json(name = "itemCount") val itemCount: Int,
    @param:Json(name = "updatedAtEpochMillis") val updatedAtEpochMillis: Long
)

@JsonClass(generateAdapter = true)
data class BasketLoadReportDto(
    @param:Json(name = "itemsLoadedCount") val itemsLoadedCount: Int,
    @param:Json(name = "priceChangeWarnings") val priceChangeWarnings: List<String> = emptyList(),
    @param:Json(name = "unavailableWarnings") val unavailableWarnings: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OptimizedLineItemDto(
    @param:Json(name = "productId") val productId: Long,
    @param:Json(name = "productName") val productName: String,
    @param:Json(name = "brand") val brand: String,
    @param:Json(name = "originalSupplierId") val originalSupplierId: Long,
    @param:Json(name = "originalSupplierName") val originalSupplierName: String,
    @param:Json(name = "originalQuantity") val originalQuantity: Int,
    @param:Json(name = "originalEffectivePrice") val originalEffectivePrice: Double,
    @param:Json(name = "suggestedSupplierId") val suggestedSupplierId: Long,
    @param:Json(name = "suggestedSupplierName") val suggestedSupplierName: String,
    @param:Json(name = "suggestedQuantity") val suggestedQuantity: Int,
    @param:Json(name = "suggestedFreeQty") val suggestedFreeQty: Int,
    @param:Json(name = "suggestedUnitPrice") val suggestedUnitPrice: Double,
    @param:Json(name = "suggestedEffectivePrice") val suggestedEffectivePrice: Double,
    @param:Json(name = "schemeApplied") val schemeApplied: String,
    @param:Json(name = "trustScore") val trustScore: Int? = null,
    @param:Json(name = "moqAdjusted") val moqAdjusted: Boolean = false,
    @param:Json(name = "reason") val reason: String
)

@JsonClass(generateAdapter = true)
data class OptimizationResultDto(
    @param:Json(name = "isExact") val isExact: Boolean,
    @param:Json(name = "currentTotal") val currentTotal: Double,
    @param:Json(name = "optimizedTotal") val optimizedTotal: Double,
    @param:Json(name = "savingsRupees") val savingsRupees: Double,
    @param:Json(name = "savingsPercent") val savingsPercent: Double,
    @param:Json(name = "lines") val lines: List<OptimizedLineItemDto> = emptyList(),
    @param:Json(name = "whySuggestions") val whySuggestions: List<String> = emptyList(),
    @param:Json(name = "assumptions") val assumptions: List<String> = emptyList()
)

// --- 5. ORDERS & TRACKING ---

@JsonClass(generateAdapter = true)
data class CreateOrderRequestDto(
    @param:Json(name = "deliveryAddress") val deliveryAddress: String,
    @param:Json(name = "notes") val notes: String = "",
    @param:Json(name = "idempotencyKey") val idempotencyKey: String? = null
)

@JsonClass(generateAdapter = true)
data class OrderDto(
    @param:Json(name = "id") val id: Long,
    @param:Json(name = "orderNumber") val orderNumber: String,
    @param:Json(name = "buyerId") val buyerId: Long,
    @param:Json(name = "supplierId") val supplierId: Long,
    @param:Json(name = "status") val status: String,
    @param:Json(name = "subtotal") val subtotal: Double,
    @param:Json(name = "gstAmount") val gstAmount: Double,
    @param:Json(name = "deliveryCharge") val deliveryCharge: Double,
    @param:Json(name = "totalAmount") val totalAmount: Double,
    @param:Json(name = "deliveryAddress") val deliveryAddress: String,
    @param:Json(name = "notes") val notes: String = "",
    @param:Json(name = "isEwayBillGenerated") val isEwayBillGenerated: Boolean = false,
    @param:Json(name = "ewayBillNumber") val ewayBillNumber: String? = null,
    @param:Json(name = "vehicleNumber") val vehicleNumber: String? = null,
    @param:Json(name = "transporterName") val transporterName: String? = null,
    @param:Json(name = "expectedDeliveryEpochMillis") val expectedDeliveryEpochMillis: Long? = null,
    @param:Json(name = "dispatchEpochMillis") val dispatchEpochMillis: Long? = null,
    @param:Json(name = "deliveredEpochMillis") val deliveredEpochMillis: Long? = null,
    @param:Json(name = "invoiceNumber") val invoiceNumber: String? = null,
    @param:Json(name = "invoiceIssuedAtEpochMillis") val invoiceIssuedAtEpochMillis: Long? = null,
    @param:Json(name = "createdAtEpochMillis") val createdAtEpochMillis: Long
)

@JsonClass(generateAdapter = true)
data class OrderItemDto(
    @param:Json(name = "id") val id: Long,
    @param:Json(name = "orderId") val orderId: Long,
    @param:Json(name = "productId") val productId: Long,
    @param:Json(name = "offerId") val offerId: Long,
    @param:Json(name = "productName") val productName: String,
    @param:Json(name = "brand") val brand: String,
    @param:Json(name = "packSize") val packSize: String,
    @param:Json(name = "hsnCode") val hsnCode: String,
    @param:Json(name = "gstRatePercent") val gstRatePercent: Double,
    @param:Json(name = "orderedQuantity") val orderedQuantity: Int,
    @param:Json(name = "freeQuantity") val freeQuantity: Int = 0,
    @param:Json(name = "fulfilledQuantity") val fulfilledQuantity: Int = 0,
    @param:Json(name = "unitPricePtr") val unitPricePtr: Double,
    @param:Json(name = "effectivePrice") val effectivePrice: Double,
    @param:Json(name = "batchNumber") val batchNumber: String? = null,
    @param:Json(name = "expiryDateEpochMillis") val expiryDateEpochMillis: Long? = null
)

@JsonClass(generateAdapter = true)
data class OrderStatusHistoryDto(
    @param:Json(name = "id") val id: Long,
    @param:Json(name = "orderId") val orderId: Long,
    @param:Json(name = "status") val status: String,
    @param:Json(name = "note") val note: String = "",
    @param:Json(name = "changedAtEpochMillis") val changedAtEpochMillis: Long,
    @param:Json(name = "changedByUserId") val changedByUserId: Long
)

@JsonClass(generateAdapter = true)
data class ProofOfDeliveryDto(
    @param:Json(name = "id") val id: Long,
    @param:Json(name = "orderId") val orderId: Long,
    @param:Json(name = "receivedDateEpochMillis") val receivedDateEpochMillis: Long,
    @param:Json(name = "receivedByName") val receivedByName: String,
    @param:Json(name = "notes") val notes: String = "",
    @param:Json(name = "photoUri") val photoUri: String? = null,
    @param:Json(name = "shortQuantity") val shortQuantity: Int = 0,
    @param:Json(name = "damagedQuantity") val damagedQuantity: Int = 0
)

@JsonClass(generateAdapter = true)
data class OrderDetailsDto(
    @param:Json(name = "order") val order: OrderDto,
    @param:Json(name = "buyer") val buyer: UserDto? = null,
    @param:Json(name = "supplier") val supplier: UserDto? = null,
    @param:Json(name = "items") val items: List<OrderItemDto> = emptyList(),
    @param:Json(name = "history") val history: List<OrderStatusHistoryDto> = emptyList(),
    @param:Json(name = "pod") val pod: ProofOfDeliveryDto? = null
)

@JsonClass(generateAdapter = true)
data class UpdateOrderStatusRequestDto(
    @param:Json(name = "newStatus") val newStatus: String,
    @param:Json(name = "note") val note: String,
    @param:Json(name = "dispatchEpochMillis") val dispatchEpochMillis: Long? = null,
    @param:Json(name = "expectedDeliveryEpochMillis") val expectedDeliveryEpochMillis: Long? = null,
    @param:Json(name = "ewayBillNumber") val ewayBillNumber: String? = null,
    @param:Json(name = "vehicleNumber") val vehicleNumber: String? = null,
    @param:Json(name = "transporterName") val transporterName: String? = null
)

@JsonClass(generateAdapter = true)
data class FulfillOrderItemRequestDto(
    @param:Json(name = "fulfilledQty") val fulfilledQty: Int,
    @param:Json(name = "batchNumber") val batchNumber: String? = null,
    @param:Json(name = "expiryDateEpochMillis") val expiryDateEpochMillis: Long? = null
)

@JsonClass(generateAdapter = true)
data class SubmitPodRequestDto(
    @param:Json(name = "receivedByName") val receivedByName: String,
    @param:Json(name = "notes") val notes: String,
    @param:Json(name = "photoUri") val photoUri: String? = null,
    @param:Json(name = "shortQuantity") val shortQuantity: Int = 0,
    @param:Json(name = "damagedQuantity") val damagedQuantity: Int = 0
)

@JsonClass(generateAdapter = true)
data class TaxInvoicePartyDto(
    @param:Json(name = "legalName") val legalName: String? = null,
    @param:Json(name = "contactName") val contactName: String? = null,
    @param:Json(name = "address") val address: String? = null,
    @param:Json(name = "shippingAddress") val shippingAddress: String? = null,
    @param:Json(name = "gstin") val gstin: String? = null,
    @param:Json(name = "phone") val phone: String? = null,
    @param:Json(name = "email") val email: String? = null
)

@JsonClass(generateAdapter = true)
data class TaxInvoiceLineItemDto(
    @param:Json(name = "productName") val productName: String,
    @param:Json(name = "brand") val brand: String = "",
    @param:Json(name = "packSize") val packSize: String = "",
    @param:Json(name = "hsnCode") val hsnCode: String = "",
    @param:Json(name = "batchNumber") val batchNumber: String? = null,
    @param:Json(name = "quantity") val quantity: Int,
    @param:Json(name = "freeQuantity") val freeQuantity: Int = 0,
    @param:Json(name = "unitPricePtr") val unitPricePtr: Double,
    @param:Json(name = "effectiveUnitPrice") val effectiveUnitPrice: Double,
    @param:Json(name = "taxableValue") val taxableValue: Double,
    @param:Json(name = "gstRatePercent") val gstRatePercent: Double,
    @param:Json(name = "gstAmount") val gstAmount: Double? = null,
    @param:Json(name = "cgstAmount") val cgstAmount: Double? = null,
    @param:Json(name = "sgstAmount") val sgstAmount: Double? = null,
    @param:Json(name = "igstAmount") val igstAmount: Double? = null,
    @param:Json(name = "lineTotal") val lineTotal: Double
)

@JsonClass(generateAdapter = true)
data class TaxInvoiceDto(
    @param:Json(name = "orderId") val orderId: Long,
    @param:Json(name = "orderNumber") val orderNumber: String,
    @param:Json(name = "orderStatus") val orderStatus: String,
    @param:Json(name = "orderDateEpochMillis") val orderDateEpochMillis: Long,
    @param:Json(name = "invoiceNumber") val invoiceNumber: String? = null,
    @param:Json(name = "invoiceDateEpochMillis") val invoiceDateEpochMillis: Long? = null,
    @param:Json(name = "isEligible") val isEligible: Boolean = false,
    @param:Json(name = "eligibilityReason") val eligibilityReason: String? = null,
    @param:Json(name = "seller") val seller: TaxInvoicePartyDto = TaxInvoicePartyDto(),
    @param:Json(name = "buyer") val buyer: TaxInvoicePartyDto = TaxInvoicePartyDto(),
    @param:Json(name = "placeOfSupply") val placeOfSupply: String? = null,
    @param:Json(name = "gstTaxType") val gstTaxType: String? = null,
    @param:Json(name = "items") val items: List<TaxInvoiceLineItemDto> = emptyList(),
    @param:Json(name = "subtotalTaxable") val subtotalTaxable: Double = 0.0,
    @param:Json(name = "cgstTotal") val cgstTotal: Double? = null,
    @param:Json(name = "sgstTotal") val sgstTotal: Double? = null,
    @param:Json(name = "igstTotal") val igstTotal: Double? = null,
    @param:Json(name = "totalGstAmount") val totalGstAmount: Double? = null,
    @param:Json(name = "deliveryCharge") val deliveryCharge: Double = 0.0,
    @param:Json(name = "grandTotal") val grandTotal: Double = 0.0,
    @param:Json(name = "ewayBillNumber") val ewayBillNumber: String? = null,
    @param:Json(name = "vehicleNumber") val vehicleNumber: String? = null,
    @param:Json(name = "transporterName") val transporterName: String? = null,
    @param:Json(name = "missingFields") val missingFields: List<String> = emptyList()
)

// --- 6. INVENTORY & BATCHES ---

@JsonClass(generateAdapter = true)
data class ProductInventoryDto(
    @param:Json(name = "id") val id: Long = 0,
    @param:Json(name = "productName") val productName: String,
    @param:Json(name = "sku") val sku: String,
    @param:Json(name = "currentStock") val currentStock: Int,
    @param:Json(name = "reorderThreshold") val reorderThreshold: Int,
    @param:Json(name = "productId") val productId: Long? = null,
    @param:Json(name = "saltComposition") val saltComposition: String = "",
    @param:Json(name = "dosageForm") val dosageForm: String = "Tablet",
    @param:Json(name = "packSize") val packSize: String = "Strip of 10",
    @param:Json(name = "batchNumber") val batchNumber: String = "",
    @param:Json(name = "expiryDateEpochMillis") val expiryDateEpochMillis: Long? = null,
    @param:Json(name = "unitPricePtr") val unitPricePtr: Double = 0.0,
    @param:Json(name = "mrp") val mrp: Double = 0.0,
    @param:Json(name = "storageCondition") val storageCondition: String = "ROOM_TEMPERATURE",
    @param:Json(name = "locationRack") val locationRack: String = "A-1",
    @param:Json(name = "supplierName") val supplierName: String = "",
    @param:Json(name = "isActive") val isActive: Boolean = true
)

@JsonClass(generateAdapter = true)
data class AdjustInventoryStockRequestDto(
    @param:Json(name = "newStock") val newStock: Int? = null,
    @param:Json(name = "delta") val delta: Int? = null,
    @param:Json(name = "reason") val reason: String = "Manual Adjustment"
)

@JsonClass(generateAdapter = true)
data class UpdateReorderThresholdRequestDto(
    @param:Json(name = "reorderThreshold") val reorderThreshold: Int
)

@JsonClass(generateAdapter = true)
data class BatchDto(
    @param:Json(name = "id") val id: Long = 0,
    @param:Json(name = "productId") val productId: Long,
    @param:Json(name = "supplierId") val supplierId: Long,
    @param:Json(name = "batchNumber") val batchNumber: String,
    @param:Json(name = "mfgDateEpochMillis") val mfgDateEpochMillis: Long? = null,
    @param:Json(name = "expiryDateEpochMillis") val expiryDateEpochMillis: Long,
    @param:Json(name = "initialQuantity") val initialQuantity: Int,
    @param:Json(name = "remainingQuantity") val remainingQuantity: Int,
    @param:Json(name = "storageCondition") val storageCondition: String,
    @param:Json(name = "mrp") val mrp: Double,
    @param:Json(name = "ptr") val ptr: Double,
    @param:Json(name = "productName") val productName: String? = null,
    @param:Json(name = "brand") val brand: String? = null
)

@JsonClass(generateAdapter = true)
data class AdjustBatchStockRequestDto(
    @param:Json(name = "newQuantity") val newQuantity: Int,
    @param:Json(name = "reason") val reason: String
)

// --- 7. NOTIFICATIONS ---

@JsonClass(generateAdapter = true)
data class NotificationDto(
    @param:Json(name = "id") val id: Long,
    @param:Json(name = "recipientUserId") val recipientUserId: Long,
    @param:Json(name = "type") val type: String,
    @param:Json(name = "title") val title: String,
    @param:Json(name = "message") val message: String,
    @param:Json(name = "referenceId") val referenceId: String? = null,
    @param:Json(name = "isRead") val isRead: Boolean = false,
    @param:Json(name = "createdAtEpochMillis") val createdAtEpochMillis: Long
)

// --- 8. RECALLS, RETURNS, CREDIT NOTES & LEDGER ---

@JsonClass(generateAdapter = true)
data class CreateRecallRequestDto(
    @param:Json(name = "productId") val productId: Long,
    @param:Json(name = "batchNumbersCsv") val batchNumbersCsv: String,
    @param:Json(name = "reason") val reason: String,
    @param:Json(name = "severity") val severity: String
)

@JsonClass(generateAdapter = true)
data class RecallDto(
    @param:Json(name = "id") val id: Long,
    @param:Json(name = "productId") val productId: Long,
    @param:Json(name = "productName") val productName: String,
    @param:Json(name = "batchNumbersCsv") val batchNumbersCsv: String,
    @param:Json(name = "reason") val reason: String,
    @param:Json(name = "severity") val severity: String,
    @param:Json(name = "createdBySupplierId") val createdBySupplierId: Long,
    @param:Json(name = "recallDateEpochMillis") val recallDateEpochMillis: Long,
    @param:Json(name = "isActive") val isActive: Boolean
)

@JsonClass(generateAdapter = true)
data class CreateReturnRequestDto(
    @param:Json(name = "orderId") val orderId: Long,
    @param:Json(name = "orderItemId") val orderItemId: Long,
    @param:Json(name = "quantity") val quantity: Int,
    @param:Json(name = "reason") val reason: String,
    @param:Json(name = "batchNumber") val batchNumber: String
)

@JsonClass(generateAdapter = true)
data class ReturnDecisionRequestDto(
    @param:Json(name = "isApproved") val isApproved: Boolean,
    @param:Json(name = "decisionNotes") val decisionNotes: String
)

@JsonClass(generateAdapter = true)
data class ReturnDto(
    @param:Json(name = "id") val id: Long,
    @param:Json(name = "orderId") val orderId: Long,
    @param:Json(name = "orderItemId") val orderItemId: Long,
    @param:Json(name = "buyerId") val buyerId: Long,
    @param:Json(name = "supplierId") val supplierId: Long,
    @param:Json(name = "productId") val productId: Long,
    @param:Json(name = "batchNumber") val batchNumber: String,
    @param:Json(name = "quantity") val quantity: Int,
    @param:Json(name = "reason") val reason: String,
    @param:Json(name = "status") val status: String,
    @param:Json(name = "requestedAtEpochMillis") val requestedAtEpochMillis: Long,
    @param:Json(name = "decisionAtEpochMillis") val decisionAtEpochMillis: Long? = null,
    @param:Json(name = "decisionNotes") val decisionNotes: String = ""
)

@JsonClass(generateAdapter = true)
data class CreditNoteDto(
    @param:Json(name = "id") val id: Long,
    @param:Json(name = "creditNoteNumber") val creditNoteNumber: String,
    @param:Json(name = "returnId") val returnId: Long,
    @param:Json(name = "orderId") val orderId: Long,
    @param:Json(name = "buyerId") val buyerId: Long,
    @param:Json(name = "supplierId") val supplierId: Long,
    @param:Json(name = "amount") val amount: Double,
    @param:Json(name = "gstAmount") val gstAmount: Double,
    @param:Json(name = "totalAmount") val totalAmount: Double,
    @param:Json(name = "issuedAtEpochMillis") val issuedAtEpochMillis: Long
)

@JsonClass(generateAdapter = true)
data class CreditLedgerEntryDto(
    @param:Json(name = "id") val id: Long,
    @param:Json(name = "buyerId") val buyerId: Long,
    @param:Json(name = "supplierId") val supplierId: Long,
    @param:Json(name = "entryType") val entryType: String,
    @param:Json(name = "amount") val amount: Double,
    @param:Json(name = "balanceAfter") val balanceAfter: Double,
    @param:Json(name = "referenceType") val referenceType: String,
    @param:Json(name = "referenceId") val referenceId: String,
    @param:Json(name = "description") val description: String,
    @param:Json(name = "dateEpochMillis") val dateEpochMillis: Long
)

@JsonClass(generateAdapter = true)
data class LedgerBalanceDto(
    @param:Json(name = "counterpartyUserId") val counterpartyUserId: Long,
    @param:Json(name = "balance") val balance: Double
)

@JsonClass(generateAdapter = true)
data class RecordLedgerPaymentRequestDto(
    @param:Json(name = "buyerId") val buyerId: Long,
    @param:Json(name = "amount") val amount: Double,
    @param:Json(name = "notes") val notes: String
)

// --- 9. ANALYTICS, TRUST SCORE & SHORTAGES ---

@JsonClass(generateAdapter = true)
data class TrustScoreDto(
    @param:Json(name = "supplierId") val supplierId: Long,
    @param:Json(name = "supplierName") val supplierName: String,
    @param:Json(name = "overallScore") val overallScore: Int,
    @param:Json(name = "hasSufficientData") val hasSufficientData: Boolean,
    @param:Json(name = "completedOrdersCount") val completedOrdersCount: Int,
    @param:Json(name = "fulfilmentRate") val fulfilmentRate: Double,
    @param:Json(name = "onTimeDeliveryRate") val onTimeDeliveryRate: Double,
    @param:Json(name = "cancellationRate") val cancellationRate: Double,
    @param:Json(name = "discrepancyRate") val discrepancyRate: Double,
    @param:Json(name = "returnRate") val returnRate: Double,
    @param:Json(name = "lastCalculatedEpochMillis") val lastCalculatedEpochMillis: Long
)

@JsonClass(generateAdapter = true)
data class ShortageAlertDto(
    @param:Json(name = "productId") val productId: Long,
    @param:Json(name = "productName") val productName: String,
    @param:Json(name = "brand") val brand: String,
    @param:Json(name = "saltComposition") val saltComposition: String,
    @param:Json(name = "riskLevel") val riskLevel: String,
    @param:Json(name = "triggeredReasons") val triggeredReasons: List<String> = emptyList(),
    @param:Json(name = "availableSuppliersCount") val availableSuppliersCount: Int,
    @param:Json(name = "totalAvailableQuantity") val totalAvailableQuantity: Int,
    @param:Json(name = "avgLeadTimeDays") val avgLeadTimeDays: Double,
    @param:Json(name = "lastUpdatedEpochMillis") val lastUpdatedEpochMillis: Long
)

@JsonClass(generateAdapter = true)
data class DashboardStatsDto(
    @param:Json(name = "role") val role: String,
    @param:Json(name = "openOrdersCount") val openOrdersCount: Int = 0,
    @param:Json(name = "basketItemsCount") val basketItemsCount: Int = 0,
    @param:Json(name = "nearExpiryBatchesCount") val nearExpiryBatchesCount: Int = 0,
    @param:Json(name = "shortageAlertsCount") val shortageAlertsCount: Int = 0,
    @param:Json(name = "ledgerBalance") val ledgerBalance: Double = 0.0,
    @param:Json(name = "activeOffersCount") val activeOffersCount: Int = 0,
    @param:Json(name = "pendingOrdersCount") val pendingOrdersCount: Int = 0,
    @param:Json(name = "lowStockCount") val lowStockCount: Int = 0,
    @param:Json(name = "trustScore") val trustScore: Int? = null,
    @param:Json(name = "totalUsersCount") val totalUsersCount: Int = 0,
    @param:Json(name = "manufacturersCount") val manufacturersCount: Int = 0,
    @param:Json(name = "pharmaCompaniesCount") val pharmaCompaniesCount: Int = 0,
    @param:Json(name = "stockistsCount") val stockistsCount: Int = 0,
    @param:Json(name = "totalProductsCount") val totalProductsCount: Int = 0,
    @param:Json(name = "totalOrdersCount") val totalOrdersCount: Int = 0,
    @param:Json(name = "activeRecallsCount") val activeRecallsCount: Int = 0
)
