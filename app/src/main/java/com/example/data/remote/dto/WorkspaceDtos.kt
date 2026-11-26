package com.example.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * [PROPOSED — AWAITING BACKEND CONFIRMATION]
 * Stateless Spring Boot + PostgreSQL JDBC DTOs for Organisations, Memberships,
 * Medicine Approval Workflow, Company Master Data, Manufacturer Banners/Campaigns/Announcements,
 * and Stockist Purchased Inventory.
 */

@JsonClass(generateAdapter = true)
data class OrganizationSummaryDto(
    @param:Json(name = "id") val id: Long,
    @param:Json(name = "name") val name: String,
    @param:Json(name = "type") val type: String,
    @param:Json(name = "gstin") val gstin: String? = null,
    @param:Json(name = "drugLicenseNumber") val drugLicenseNumber: String? = null,
    @param:Json(name = "address") val address: String = "",
    @param:Json(name = "active") val isActive: Boolean = true,
    @param:Json(name = "createdAtEpochMillis") val createdAtEpochMillis: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class OrganizationMemberDto(
    @param:Json(name = "id") val id: Long,
    @param:Json(name = "organizationId") val organizationId: Long,
    @param:Json(name = "userId") val userId: Long,
    @param:Json(name = "fullName") val fullName: String,
    @param:Json(name = "email") val email: String,
    @param:Json(name = "roles") val roles: List<String>,
    @param:Json(name = "permissions") val permissions: List<String>,
    @param:Json(name = "active") val isActive: Boolean = true,
    @param:Json(name = "joinedAtEpochMillis") val joinedAtEpochMillis: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class AuthContextDto(
    @param:Json(name = "user") val user: UserDto,
    @param:Json(name = "organization") val organization: OrganizationSummaryDto,
    @param:Json(name = "roles") val roles: List<String>,
    @param:Json(name = "permissions") val permissions: List<String>,
    @param:Json(name = "authorizedWorkspaces") val authorizedWorkspaces: List<String>
)

@JsonClass(generateAdapter = true)
data class AssignMemberRolesRequestDto(
    @param:Json(name = "roles") val roles: List<String>,
    @param:Json(name = "permissions") val permissions: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class MedicineSubmissionDto(
    @param:Json(name = "id") val id: Long = 0L,
    @param:Json(name = "organizationId") val organizationId: Long = 0L,
    @param:Json(name = "organizationName") val organizationName: String = "",
    @param:Json(name = "submittedByUserId") val submittedByUserId: Long = 0L,
    @param:Json(name = "submittedByName") val submittedByName: String = "",
    @param:Json(name = "submittedByRole") val submittedByRole: String = "MANUFACTURER",
    @param:Json(name = "medicineName") val medicineName: String,
    @param:Json(name = "brandName") val brandName: String,
    @param:Json(name = "genericName") val genericName: String,
    @param:Json(name = "saltComposition") val saltComposition: String,
    @param:Json(name = "dosageForm") val dosageForm: String,
    @param:Json(name = "strength") val strength: String,
    @param:Json(name = "routeOfAdministration") val routeOfAdministration: String = "Oral",
    @param:Json(name = "packSize") val packSize: String,
    @param:Json(name = "packagingType") val packagingType: String = "Blister Strip",
    @param:Json(name = "therapeuticCategory") val therapeuticCategory: String,
    @param:Json(name = "storageCondition") val storageCondition: String = "ROOM_TEMPERATURE",
    @param:Json(name = "rx") val isRx: Boolean = true,
    @param:Json(name = "mrp") val mrp: Double = 0.0,
    @param:Json(name = "wholesaleRatePtr") val wholesaleRatePtr: Double = 0.0,
    @param:Json(name = "hsnCode") val hsnCode: String = "30049099",
    @param:Json(name = "gstRatePercent") val gstRatePercent: Double = 12.0,
    @param:Json(name = "skuCode") val skuCode: String = "",
    @param:Json(name = "description") val description: String = "",
    @param:Json(name = "imageUrl") val imageUrl: String? = null,
    @param:Json(name = "regulatoryIdentifier") val regulatoryIdentifier: String? = null,
    @param:Json(name = "supportingDocUri") val supportingDocUri: String? = null,
    @param:Json(name = "approvalStatus") val approvalStatus: String = "DRAFT",
    @param:Json(name = "catalogueStatus") val catalogueStatus: String = "INACTIVE",
    @param:Json(name = "approvedProductId") val approvedProductId: Long? = null,
    @param:Json(name = "reviewedByUserId") val reviewedByUserId: Long? = null,
    @param:Json(name = "reviewedByName") val reviewedByName: String? = null,
    @param:Json(name = "rejectionReason") val rejectionReason: String? = null,
    @param:Json(name = "reviewNotes") val reviewNotes: String? = null,
    @param:Json(name = "submittedAtEpochMillis") val submittedAtEpochMillis: Long = System.currentTimeMillis(),
    @param:Json(name = "reviewedAtEpochMillis") val reviewedAtEpochMillis: Long? = null,
    @param:Json(name = "updatedAtEpochMillis") val updatedAtEpochMillis: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class ApprovalDecisionRequestDto(
    @param:Json(name = "notes") val notes: String? = null,
    @param:Json(name = "reason") val reason: String? = null
)

@JsonClass(generateAdapter = true)
data class ApprovalAuditEntryDto(
    @param:Json(name = "id") val id: Long,
    @param:Json(name = "submissionId") val submissionId: Long,
    @param:Json(name = "organizationId") val organizationId: Long,
    @param:Json(name = "action") val action: String,
    @param:Json(name = "fromStatus") val fromStatus: String,
    @param:Json(name = "toStatus") val toStatus: String,
    @param:Json(name = "actorUserId") val actorUserId: Long,
    @param:Json(name = "actorName") val actorName: String,
    @param:Json(name = "notes") val notes: String,
    @param:Json(name = "timestampEpochMillis") val timestampEpochMillis: Long
)

@JsonClass(generateAdapter = true)
data class UpdateCatalogueStatusRequestDto(
    @param:Json(name = "catalogueStatus") val catalogueStatus: String
)

@JsonClass(generateAdapter = true)
data class MasterDataDto(
    @param:Json(name = "id") val id: Long = 0L,
    @param:Json(name = "organizationId") val organizationId: Long? = null,
    @param:Json(name = "type") val type: String,
    @param:Json(name = "name") val name: String,
    @param:Json(name = "code") val code: String = "",
    @param:Json(name = "description") val description: String = "",
    @param:Json(name = "organizationScoped") val isOrganizationScoped: Boolean = false,
    @param:Json(name = "active") val isActive: Boolean = true,
    @param:Json(name = "createdAtEpochMillis") val createdAtEpochMillis: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class OfferBannerDto(
    @param:Json(name = "id") val id: Long = 0L,
    @param:Json(name = "organizationId") val organizationId: Long = 0L,
    @param:Json(name = "organizationName") val organizationName: String = "",
    @param:Json(name = "title") val title: String,
    @param:Json(name = "subtitle") val subtitle: String,
    @param:Json(name = "imageUrl") val imageUrl: String? = null,
    @param:Json(name = "offerType") val offerType: String = "PERCENT_DISCOUNT",
    @param:Json(name = "discountOrOfferSummary") val discountOrOfferSummary: String,
    @param:Json(name = "linkedProductId") val linkedProductId: Long? = null,
    @param:Json(name = "linkedProductName") val linkedProductName: String? = null,
    @param:Json(name = "linkedCategory") val linkedCategory: String? = null,
    @param:Json(name = "linkedCampaignId") val linkedCampaignId: Long? = null,
    @param:Json(name = "startDateEpochMillis") val startDateEpochMillis: Long,
    @param:Json(name = "endDateEpochMillis") val endDateEpochMillis: Long,
    @param:Json(name = "priorityOrder") val priorityOrder: Int = 1,
    @param:Json(name = "status") val status: String = "DRAFT",
    @param:Json(name = "targetAudience") val targetAudience: String = "ALL_STOCKISTS",
    @param:Json(name = "ctaText") val ctaText: String = "View Offer",
    @param:Json(name = "ctaDestinationRoute") val ctaDestinationRoute: String = "",
    @param:Json(name = "createdByUserId") val createdByUserId: Long = 0L,
    @param:Json(name = "createdAtEpochMillis") val createdAtEpochMillis: Long = System.currentTimeMillis(),
    @param:Json(name = "updatedAtEpochMillis") val updatedAtEpochMillis: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class UpdateBannerStatusRequestDto(
    @param:Json(name = "status") val status: String
)

@JsonClass(generateAdapter = true)
data class PromotionalCampaignDto(
    @param:Json(name = "id") val id: Long = 0L,
    @param:Json(name = "organizationId") val organizationId: Long = 0L,
    @param:Json(name = "organizationName") val organizationName: String = "",
    @param:Json(name = "name") val name: String,
    @param:Json(name = "schemeSummary") val schemeSummary: String,
    @param:Json(name = "discountPercent") val discountPercent: Double = 0.0,
    @param:Json(name = "minOrderQuantity") val minOrderQuantity: Int = 10,
    @param:Json(name = "linkedProductIdsCsv") val linkedProductIdsCsv: String = "",
    @param:Json(name = "linkedProductNamesSummary") val linkedProductNamesSummary: String = "",
    @param:Json(name = "termsAndConditions") val termsAndConditions: String = "",
    @param:Json(name = "startDateEpochMillis") val startDateEpochMillis: Long,
    @param:Json(name = "endDateEpochMillis") val endDateEpochMillis: Long,
    @param:Json(name = "status") val status: String = "ACTIVE",
    @param:Json(name = "redemptionsCount") val redemptionsCount: Int = 0,
    @param:Json(name = "createdByUserId") val createdByUserId: Long = 0L,
    @param:Json(name = "createdAtEpochMillis") val createdAtEpochMillis: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class MedicineAnnouncementDto(
    @param:Json(name = "id") val id: Long = 0L,
    @param:Json(name = "organizationId") val organizationId: Long = 0L,
    @param:Json(name = "organizationName") val organizationName: String = "",
    @param:Json(name = "linkedProductId") val linkedProductId: Long,
    @param:Json(name = "linkedProductName") val linkedProductName: String,
    @param:Json(name = "title") val title: String,
    @param:Json(name = "body") val body: String,
    @param:Json(name = "category") val category: String = "NEW_LAUNCH",
    @param:Json(name = "imageUrl") val imageUrl: String? = null,
    @param:Json(name = "status") val status: String = "DRAFT",
    @param:Json(name = "createdByUserId") val createdByUserId: Long = 0L,
    @param:Json(name = "publishedAtEpochMillis") val publishedAtEpochMillis: Long? = null,
    @param:Json(name = "createdAtEpochMillis") val createdAtEpochMillis: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class StockistPurchasedInventoryDto(
    @param:Json(name = "id") val id: Long = 0L,
    @param:Json(name = "stockistOrganizationId") val stockistOrganizationId: Long = 0L,
    @param:Json(name = "stockistUserId") val stockistUserId: Long = 0L,
    @param:Json(name = "productId") val productId: Long,
    @param:Json(name = "productName") val productName: String,
    @param:Json(name = "brand") val brand: String,
    @param:Json(name = "saltComposition") val saltComposition: String,
    @param:Json(name = "batchNumber") val batchNumber: String,
    @param:Json(name = "expiryDateEpochMillis") val expiryDateEpochMillis: Long,
    @param:Json(name = "purchasedQuantity") val purchasedQuantity: Int,
    @param:Json(name = "remainingQuantity") val remainingQuantity: Int,
    @param:Json(name = "reorderLevel") val reorderLevel: Int = 20,
    @param:Json(name = "purchaseUnitPrice") val purchaseUnitPrice: Double,
    @param:Json(name = "sourceOrderNumber") val sourceOrderNumber: String? = null,
    @param:Json(name = "updatedAtEpochMillis") val updatedAtEpochMillis: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class MediaUploadResponseDto(
    @param:Json(name = "assetUrl") val assetUrl: String,
    @param:Json(name = "contentType") val contentType: String = "image/jpeg",
    @param:Json(name = "sizeBytes") val sizeBytes: Long = 0L
)
