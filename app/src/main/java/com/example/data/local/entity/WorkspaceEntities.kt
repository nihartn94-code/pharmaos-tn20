package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "organizations",
    indices = [Index(value = ["type"]), Index(value = ["name"])]
)
data class OrganizationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val type: String, // STOCKIST | PHARMACEUTICAL_ORGANIZATION
    val gstin: String? = null,
    val drugLicenseNumber: String? = null,
    val address: String = "",
    val isActive: Boolean = true,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "organization_members",
    indices = [
        Index(value = ["organizationId"]),
        Index(value = ["userId"]),
        Index(value = ["organizationId", "userId"], unique = true)
    ]
)
data class OrganizationMemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val organizationId: Long,
    val userId: Long,
    val fullName: String,
    val email: String,
    val rolesCsv: String,
    val permissionsCsv: String,
    val isActive: Boolean = true,
    val joinedAtEpochMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "medicine_submissions",
    indices = [
        Index(value = ["organizationId"]),
        Index(value = ["approvalStatus"]),
        Index(value = ["submittedByUserId"])
    ]
)
data class MedicineSubmissionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val organizationId: Long,
    val organizationName: String,
    val submittedByUserId: Long,
    val submittedByName: String,
    val submittedByRole: String,
    val medicineName: String,
    val brandName: String,
    val genericName: String,
    val saltComposition: String,
    val dosageForm: String,
    val strength: String,
    val routeOfAdministration: String = "Oral",
    val packSize: String,
    val packagingType: String = "Blister Strip",
    val therapeuticCategory: String,
    val storageCondition: String = "ROOM_TEMPERATURE",
    val isRx: Boolean = true,
    val mrp: Double = 0.0,
    val wholesaleRatePtr: Double = 0.0,
    val hsnCode: String = "",
    val gstRatePercent: Double = 0.0,
    val skuCode: String = "",
    val description: String = "",
    val imageUrl: String? = null,
    val regulatoryIdentifier: String? = null,
    val supportingDocUri: String? = null,
    val approvalStatus: String = "DRAFT", // DRAFT | PENDING_APPROVAL | APPROVED | REJECTED | WITHDRAWN
    val catalogueStatus: String = "INACTIVE", // ACTIVE | INACTIVE | ARCHIVED
    val approvedProductId: Long? = null,
    val reviewedByUserId: Long? = null,
    val reviewedByName: String? = null,
    val rejectionReason: String? = null,
    val reviewNotes: String? = null,
    val submittedAtEpochMillis: Long = System.currentTimeMillis(),
    val reviewedAtEpochMillis: Long? = null,
    val updatedAtEpochMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "approval_audit_logs",
    indices = [
        Index(value = ["submissionId"]),
        Index(value = ["organizationId"])
    ]
)
data class ApprovalAuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val submissionId: Long,
    val organizationId: Long,
    val action: String,
    val fromStatus: String,
    val toStatus: String,
    val actorUserId: Long,
    val actorName: String,
    val notes: String,
    val timestampEpochMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "master_data_entries",
    indices = [
        Index(value = ["type"]),
        Index(value = ["organizationId"]),
        Index(value = ["type", "normalizedName", "organizationId"], unique = true)
    ]
)
data class MasterDataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val organizationId: Long = 0L, // 0 = platform standard, >0 = org scoped
    val type: String, // SALT_COMPOSITION | GENERIC_MEDICINE | DOSAGE_FORM | STRENGTH | BRAND | PACKAGING_TYPE | PRODUCT_CLASSIFICATION
    val name: String,
    val normalizedName: String,
    val code: String = "",
    val description: String = "",
    val isOrganizationScoped: Boolean = false,
    val isActive: Boolean = true,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "offer_banners",
    indices = [
        Index(value = ["organizationId"]),
        Index(value = ["status"]),
        Index(value = ["priorityOrder"])
    ]
)
data class OfferBannerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val organizationId: Long,
    val organizationName: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String? = null,
    val offerType: String = "PERCENT_DISCOUNT",
    val discountOrOfferSummary: String,
    val linkedProductId: Long? = null,
    val linkedProductName: String? = null,
    val linkedCategory: String? = null,
    val linkedCampaignId: Long? = null,
    val startDateEpochMillis: Long,
    val endDateEpochMillis: Long,
    val priorityOrder: Int = 1,
    val status: String = "DRAFT", // DRAFT | SCHEDULED | ACTIVE | EXPIRED | INACTIVE | ARCHIVED
    val targetAudience: String = "ALL_STOCKISTS",
    val ctaText: String = "View Offer",
    val ctaDestinationRoute: String = "",
    val createdByUserId: Long,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val updatedAtEpochMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "promotional_campaigns",
    indices = [
        Index(value = ["organizationId"]),
        Index(value = ["status"])
    ]
)
data class PromotionalCampaignEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val organizationId: Long,
    val organizationName: String,
    val name: String,
    val schemeSummary: String,
    val discountPercent: Double = 0.0,
    val minOrderQuantity: Int = 10,
    val linkedProductIdsCsv: String = "",
    val linkedProductNamesSummary: String = "",
    val termsAndConditions: String = "",
    val startDateEpochMillis: Long,
    val endDateEpochMillis: Long,
    val status: String = "ACTIVE", // DRAFT | ACTIVE | PAUSED | EXPIRED | ARCHIVED
    val redemptionsCount: Int = 0,
    val createdByUserId: Long,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "medicine_announcements",
    indices = [
        Index(value = ["organizationId"]),
        Index(value = ["linkedProductId"]),
        Index(value = ["status"])
    ]
)
data class MedicineAnnouncementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val organizationId: Long,
    val organizationName: String,
    val linkedProductId: Long,
    val linkedProductName: String,
    val title: String,
    val body: String,
    val category: String = "NEW_LAUNCH",
    val imageUrl: String? = null,
    val status: String = "DRAFT", // DRAFT | PUBLISHED | ARCHIVED
    val createdByUserId: Long,
    val publishedAtEpochMillis: Long? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)

/**
 * Strictly isolated Stockist Purchased Inventory cache table.
 * NEVER shared with Manufacturer real-time warehouse stock or batch tables.
 */
@Entity(
    tableName = "stockist_purchased_inventory",
    indices = [
        Index(value = ["stockistOrganizationId"]),
        Index(value = ["stockistUserId"]),
        Index(value = ["productId"])
    ]
)
data class StockistPurchasedInventoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val stockistOrganizationId: Long,
    val stockistUserId: Long,
    val productId: Long,
    val productName: String,
    val brand: String,
    val saltComposition: String,
    val batchNumber: String,
    val expiryDateEpochMillis: Long,
    val purchasedQuantity: Int,
    val remainingQuantity: Int,
    val reorderLevel: Int = 20,
    val purchaseUnitPrice: Double,
    val sourceOrderNumber: String? = null,
    val updatedAtEpochMillis: Long = System.currentTimeMillis()
)
