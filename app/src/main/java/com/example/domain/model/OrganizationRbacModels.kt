package com.example.domain.model

/**
 * Primary organization types in PharmaOS.
 *
 * CRITICAL ARCHITECTURE RULE:
 * Manufacturer and Company belong to the SAME [PHARMACEUTICAL_ORGANIZATION] entity.
 * They are distinguished by employee memberships, assigned roles, and granular permissions —
 * never by creating duplicate pharmaceutical organizations.
 */
enum class OrganizationType(val displayName: String) {
    STOCKIST("Stockist Organization"),
    PHARMACEUTICAL_ORGANIZATION("Pharmaceutical Organization");

    companion object {
        fun fromString(raw: String?, fallbackRole: UserRole = UserRole.STOCKIST): OrganizationType {
            if (!raw.isNullOrBlank()) {
                entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }?.let { return it }
            }
            return when (fallbackRole) {
                UserRole.STOCKIST -> STOCKIST
                UserRole.MANUFACTURER, UserRole.PHARMA_COMPANY, UserRole.ADMIN -> PHARMACEUTICAL_ORGANIZATION
            }
        }
    }
}

/**
 * Distinct application workspaces rendered after authentication.
 * Navigation destination is always determined by authenticated permissions and memberships.
 */
enum class WorkspaceType(val displayName: String, val badgeLabel: String) {
    STOCKIST("Stockist Workspace", "Stockist"),
    MANUFACTURER("Manufacturer Workspace", "Manufacturer"),
    COMPANY("Company Workspace", "Company"),
    ADMIN("Administrator Workspace", "Admin");

    companion object {
        fun fromString(raw: String?): WorkspaceType? {
            if (raw.isNullOrBlank()) return null
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
        }
    }
}

/**
 * Granular permissions controlling UI visibility, navigation route guards,
 * repository operations, and proposed Spring Boot backend authorization.
 */
enum class PharmaPermission(val description: String) {
    PRODUCT_VIEW("View approved medicine catalogue and details"),
    BASKET_MANAGE("Manage procurement basket and saved templates"),
    ORDER_CREATE("Create purchase orders from basket"),
    ORDER_VIEW("View organization orders and tracking"),
    STOCKIST_INVENTORY_MANAGE("Manage stockist purchased inventory separately from manufacturer inventory"),
    INVOICE_VIEW("View and download GST tax invoices"),

    BANNER_CREATE("Create manufacturer offer banners"),
    BANNER_UPDATE("Update, schedule, activate, deactivate, or archive own organization banners"),
    BANNER_DELETE("Delete draft or inactive banners owned by organization"),
    CAMPAIGN_MANAGE("Create and manage promotional campaigns and trade schemes"),
    MEDICINE_ANNOUNCEMENT_CREATE("Publish medicine announcements for approved organization products"),

    MEDICINE_SUBMIT("Submit new medicine proposals for company approval"),
    MEDICINE_EDIT("Edit draft/rejected submissions or authorized catalogue fields"),
    MEDICINE_APPROVE("Approve pending medicine submissions into active catalogue"),
    MEDICINE_REJECT("Reject pending medicine submissions with mandatory reason"),
    MEDICINE_CATALOGUE_MANAGE("Activate, deactivate, or archive approved catalogue medicines"),

    COMPOSITION_MANAGE("Manage salt composition master data"),
    DOSAGE_FORM_MANAGE("Manage dosage forms, strengths, packaging, and classifications"),
    BRAND_MANAGE("Manage brand and generic medicine master data"),

    ANALYTICS_VIEW("View organization analytics and operational metrics"),
    ORGANIZATION_MEMBER_MANAGE("Manage members inside own organization"),
    ROLE_ASSIGN("Assign roles and granular permissions to organization members");

    companion object {
        fun parseCsv(csv: String?): Set<PharmaPermission> {
            if (csv.isNullOrBlank()) return emptySet()
            return csv.split(",")
                .mapNotNull { token ->
                    val trimmed = token.trim()
                    entries.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
                }
                .toSet()
        }

        fun toCsv(permissions: Set<PharmaPermission>): String =
            permissions.map { it.name }.sorted().joinToString(",")
    }
}

/**
 * Default permission sets mapped to each [UserRole].
 * Employees of a [OrganizationType.PHARMACEUTICAL_ORGANIZATION] can hold both
 * [UserRole.MANUFACTURER] and [UserRole.PHARMA_COMPANY] roles under one [organizationId],
 * receiving the union of their permissions and unlocking the Workspace Switcher.
 */
object RolePermissionMatrix {

    val stockistPermissions: Set<PharmaPermission> = setOf(
        PharmaPermission.PRODUCT_VIEW,
        PharmaPermission.BASKET_MANAGE,
        PharmaPermission.ORDER_CREATE,
        PharmaPermission.ORDER_VIEW,
        PharmaPermission.STOCKIST_INVENTORY_MANAGE,
        PharmaPermission.INVOICE_VIEW
    )

    val manufacturerPermissions: Set<PharmaPermission> = setOf(
        PharmaPermission.PRODUCT_VIEW,
        PharmaPermission.ORDER_VIEW,
        PharmaPermission.INVOICE_VIEW,
        PharmaPermission.BANNER_CREATE,
        PharmaPermission.BANNER_UPDATE,
        PharmaPermission.BANNER_DELETE,
        PharmaPermission.CAMPAIGN_MANAGE,
        PharmaPermission.MEDICINE_ANNOUNCEMENT_CREATE,
        PharmaPermission.MEDICINE_SUBMIT,
        PharmaPermission.MEDICINE_EDIT,
        PharmaPermission.ANALYTICS_VIEW
    )

    val companyPermissions: Set<PharmaPermission> = setOf(
        PharmaPermission.PRODUCT_VIEW,
        PharmaPermission.ORDER_VIEW,
        PharmaPermission.INVOICE_VIEW,
        PharmaPermission.MEDICINE_SUBMIT,
        PharmaPermission.MEDICINE_EDIT,
        PharmaPermission.MEDICINE_APPROVE,
        PharmaPermission.MEDICINE_REJECT,
        PharmaPermission.MEDICINE_CATALOGUE_MANAGE,
        PharmaPermission.COMPOSITION_MANAGE,
        PharmaPermission.DOSAGE_FORM_MANAGE,
        PharmaPermission.BRAND_MANAGE,
        PharmaPermission.ANALYTICS_VIEW,
        PharmaPermission.ORGANIZATION_MEMBER_MANAGE,
        PharmaPermission.ROLE_ASSIGN
    )

    val adminPermissions: Set<PharmaPermission> = PharmaPermission.entries.toSet()

    fun defaultPermissionsFor(role: UserRole): Set<PharmaPermission> = when (role) {
        UserRole.STOCKIST -> stockistPermissions
        UserRole.MANUFACTURER -> manufacturerPermissions
        UserRole.PHARMA_COMPANY -> companyPermissions
        UserRole.ADMIN -> adminPermissions
    }

    fun resolvePermissions(
        roles: Set<UserRole>,
        explicitPermissions: Set<PharmaPermission> = emptySet()
    ): Set<PharmaPermission> {
        if (explicitPermissions.isNotEmpty()) return explicitPermissions
        return roles.flatMap { defaultPermissionsFor(it) }.toSet()
    }

    fun parseRolesCsv(csv: String?, fallback: UserRole): Set<UserRole> {
        if (csv.isNullOrBlank()) return setOf(fallback)
        val parsed = csv.split(",")
            .mapNotNull { token ->
                UserRole.entries.firstOrNull { it.name.equals(token.trim(), ignoreCase = true) }
            }
            .toSet()
        return if (parsed.isEmpty()) setOf(fallback) else parsed
    }

    fun rolesToCsv(roles: Set<UserRole>): String = roles.map { it.name }.sorted().joinToString(",")
}

/**
 * Centralized authorization helper used by UI guards, ViewModels, and Repositories.
 * NOTE: Client checks protect UX and local cache only; production Spring Boot APIs
 * MUST enforce identical permission and organization ownership checks server-side.
 */
object AccessControlPolicy {

    fun authorizedWorkspaces(user: AuthUser): List<WorkspaceType> {
        val workspaces = linkedSetOf<WorkspaceType>()
        if (user.organizationType == OrganizationType.STOCKIST ||
            user.assignedRoles.contains(UserRole.STOCKIST) ||
            user.hasPermission(PharmaPermission.BASKET_MANAGE)
        ) {
            workspaces.add(WorkspaceType.STOCKIST)
        }
        val hasManufacturerCapability =
            user.assignedRoles.contains(UserRole.MANUFACTURER) ||
                user.hasPermission(PharmaPermission.BANNER_CREATE) ||
                user.hasPermission(PharmaPermission.CAMPAIGN_MANAGE) ||
                user.hasPermission(PharmaPermission.MEDICINE_ANNOUNCEMENT_CREATE)
        if (hasManufacturerCapability && user.organizationType == OrganizationType.PHARMACEUTICAL_ORGANIZATION) {
            workspaces.add(WorkspaceType.MANUFACTURER)
        }
        val hasCompanyCapability =
            user.assignedRoles.contains(UserRole.PHARMA_COMPANY) ||
                user.hasPermission(PharmaPermission.MEDICINE_APPROVE) ||
                user.hasPermission(PharmaPermission.MEDICINE_CATALOGUE_MANAGE) ||
                user.hasPermission(PharmaPermission.COMPOSITION_MANAGE)
        if (hasCompanyCapability && user.organizationType == OrganizationType.PHARMACEUTICAL_ORGANIZATION) {
            workspaces.add(WorkspaceType.COMPANY)
        }
        if (user.assignedRoles.contains(UserRole.ADMIN) || user.role == UserRole.ADMIN) {
            workspaces.add(WorkspaceType.ADMIN)
        }
        if (workspaces.isEmpty()) {
            workspaces.add(defaultWorkspaceForRole(user.role))
        }
        return workspaces.toList()
    }

    fun defaultWorkspaceForRole(role: UserRole): WorkspaceType = when (role) {
        UserRole.STOCKIST -> WorkspaceType.STOCKIST
        UserRole.MANUFACTURER -> WorkspaceType.MANUFACTURER
        UserRole.PHARMA_COMPANY -> WorkspaceType.COMPANY
        UserRole.ADMIN -> WorkspaceType.ADMIN
    }

    fun canAccessWorkspace(user: AuthUser?, workspace: WorkspaceType): Boolean {
        if (user == null) return false
        return authorizedWorkspaces(user).contains(workspace)
    }

    fun canAccessManufacturerRealTimeInventory(user: AuthUser?): Boolean {
        if (user == null) return false
        if (user.organizationType == OrganizationType.STOCKIST && user.role == UserRole.STOCKIST) return false
        return user.assignedRoles.contains(UserRole.MANUFACTURER) ||
            user.assignedRoles.contains(UserRole.PHARMA_COMPANY) ||
            user.assignedRoles.contains(UserRole.ADMIN)
    }

    fun canAccessStockistPurchasedInventory(user: AuthUser?): Boolean {
        if (user == null) return false
        return user.hasPermission(PharmaPermission.STOCKIST_INVENTORY_MANAGE) &&
            (user.organizationType == OrganizationType.STOCKIST || user.role == UserRole.ADMIN)
    }

    fun canOperateOnOrganization(user: AuthUser?, targetOrganizationId: Long): Boolean {
        if (user == null) return false
        if (user.role == UserRole.ADMIN || user.assignedRoles.contains(UserRole.ADMIN)) return true
        return user.organizationId > 0L && user.organizationId == targetOrganizationId
    }

    fun canApproveSubmission(
        reviewer: AuthUser?,
        submission: MedicineSubmission,
        allowSelfApproval: Boolean = false
    ): Result<Unit> {
        if (reviewer == null) {
            return Result.failure(SecurityException("Authentication required to review medicine submissions."))
        }
        if (!reviewer.hasPermission(PharmaPermission.MEDICINE_APPROVE)) {
            return Result.failure(SecurityException("Access denied: MEDICINE_APPROVE permission is required."))
        }
        if (reviewer.organizationType != OrganizationType.PHARMACEUTICAL_ORGANIZATION && reviewer.role != UserRole.ADMIN) {
            return Result.failure(SecurityException("Only Pharmaceutical Organization approvers can review submissions."))
        }
        if (!canOperateOnOrganization(reviewer, submission.organizationId)) {
            return Result.failure(SecurityException("Cross-organization approval denied: submission belongs to another organization."))
        }
        if (submission.approvalStatus != MedicineSubmissionStatus.PENDING_APPROVAL) {
            return Result.failure(IllegalStateException("Only PENDING_APPROVAL submissions can be approved (current: ${submission.approvalStatus})."))
        }
        if (!allowSelfApproval && submission.submittedByUserId == reviewer.id) {
            return Result.failure(SecurityException("Self-approval is disabled by default. Another authorized Company approver must review this submission."))
        }
        return Result.success(Unit)
    }

    fun canRejectSubmission(
        reviewer: AuthUser?,
        submission: MedicineSubmission,
        reason: String
    ): Result<Unit> {
        if (reviewer == null) {
            return Result.failure(SecurityException("Authentication required to reject medicine submissions."))
        }
        if (!reviewer.hasPermission(PharmaPermission.MEDICINE_REJECT)) {
            return Result.failure(SecurityException("Access denied: MEDICINE_REJECT permission is required."))
        }
        if (!canOperateOnOrganization(reviewer, submission.organizationId)) {
            return Result.failure(SecurityException("Cross-organization rejection denied: submission belongs to another organization."))
        }
        if (submission.approvalStatus != MedicineSubmissionStatus.PENDING_APPROVAL) {
            return Result.failure(IllegalStateException("Only PENDING_APPROVAL submissions can be rejected."))
        }
        if (reason.trim().length < 5) {
            return Result.failure(IllegalArgumentException("A clear rejection reason (at least 5 characters) is mandatory."))
        }
        return Result.success(Unit)
    }

    fun isMedicineDiscoverableByStockist(
        approvalStatus: MedicineSubmissionStatus,
        publicationStatus: CataloguePublicationStatus,
        isActive: Boolean
    ): Boolean {
        return approvalStatus == MedicineSubmissionStatus.APPROVED &&
            publicationStatus == CataloguePublicationStatus.ACTIVE &&
            isActive
    }
}

// =====================================================================
// Organisation & Membership Domain Models
// =====================================================================

data class OrganizationSummary(
    val id: Long,
    val name: String,
    val type: OrganizationType,
    val gstin: String?,
    val drugLicenseNumber: String?,
    val address: String,
    val isActive: Boolean = true,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)

data class OrganizationMember(
    val id: Long,
    val organizationId: Long,
    val userId: Long,
    val fullName: String,
    val email: String,
    val roles: Set<UserRole>,
    val permissions: Set<PharmaPermission>,
    val isActive: Boolean = true,
    val joinedAtEpochMillis: Long = System.currentTimeMillis()
)

// =====================================================================
// Compulsory Medicine Approval Workflow Models
// =====================================================================

enum class MedicineSubmissionStatus(val displayName: String) {
    DRAFT("Draft"),
    PENDING_APPROVAL("Pending Approval"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    WITHDRAWN("Withdrawn");

    companion object {
        fun fromString(raw: String?): MedicineSubmissionStatus =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: APPROVED
    }
}

enum class CataloguePublicationStatus(val displayName: String) {
    ACTIVE("Active"),
    INACTIVE("Inactive"),
    ARCHIVED("Archived");

    companion object {
        fun fromString(raw: String?): CataloguePublicationStatus =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: ACTIVE
    }
}

data class MedicineSubmission(
    val id: Long = 0L,
    val organizationId: Long,
    val organizationName: String,
    val submittedByUserId: Long,
    val submittedByName: String,
    val submittedByRole: UserRole,
    // Basic Information
    val medicineName: String,
    val brandName: String,
    val genericName: String,
    val saltComposition: String,
    // Product Information
    val dosageForm: String,
    val strength: String,
    val routeOfAdministration: String = "Oral",
    val packSize: String,
    val packagingType: String = "Blister Strip",
    val therapeuticCategory: String,
    val storageCondition: String = StorageCondition.ROOM_TEMPERATURE.name,
    val isRx: Boolean = true,
    // Commercial Information
    val mrp: Double = 0.0,
    val wholesaleRatePtr: Double = 0.0,
    val hsnCode: String = "30049099",
    val gstRatePercent: Double = 12.0,
    val skuCode: String = "",
    // Supporting Information
    val description: String = "",
    val imageUrl: String? = null,
    val regulatoryIdentifier: String? = null,
    val supportingDocUri: String? = null,
    // Workflow & Approval Metadata
    val approvalStatus: MedicineSubmissionStatus = MedicineSubmissionStatus.DRAFT,
    val catalogueStatus: CataloguePublicationStatus = CataloguePublicationStatus.INACTIVE,
    val approvedProductId: Long? = null,
    val reviewedByUserId: Long? = null,
    val reviewedByName: String? = null,
    val rejectionReason: String? = null,
    val reviewNotes: String? = null,
    val submittedAtEpochMillis: Long = System.currentTimeMillis(),
    val reviewedAtEpochMillis: Long? = null,
    val updatedAtEpochMillis: Long = System.currentTimeMillis()
)

data class ApprovalAuditEntry(
    val id: Long = 0L,
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

data class MedicineDuplicateCandidate(
    val existingProductId: Long,
    val name: String,
    val brand: String,
    val saltComposition: String,
    val strength: String,
    val dosageForm: String,
    val matchReason: String
)

// =====================================================================
// Company Master Data Models
// =====================================================================

enum class MasterDataType(val displayName: String, val scopeHint: String) {
    SALT_COMPOSITION("Salt Compositions", "Platform / Org"),
    GENERIC_MEDICINE("Generic Medicines", "Platform / Org"),
    DOSAGE_FORM("Dosage Forms", "Platform Standard"),
    STRENGTH("Strengths", "Platform Standard"),
    BRAND("Brands", "Organization Scoped"),
    PACKAGING_TYPE("Packaging Types", "Platform Standard"),
    PRODUCT_CLASSIFICATION("Product Classifications", "Platform Standard");

    companion object {
        fun fromString(raw: String?): MasterDataType =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: SALT_COMPOSITION
    }
}

data class MasterDataItem(
    val id: Long = 0L,
    val organizationId: Long?,
    val type: MasterDataType,
    val name: String,
    val code: String = "",
    val description: String = "",
    val isOrganizationScoped: Boolean = false,
    val isActive: Boolean = true,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)

// =====================================================================
// Manufacturer Offer Banners, Campaigns & Medicine Announcements
// =====================================================================

enum class BannerStatus(val displayName: String) {
    DRAFT("Draft"),
    SCHEDULED("Scheduled"),
    ACTIVE("Active"),
    EXPIRED("Expired"),
    INACTIVE("Inactive"),
    ARCHIVED("Archived");

    companion object {
        fun fromString(raw: String?): BannerStatus =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: DRAFT
    }
}

enum class BannerOfferType(val displayName: String) {
    PERCENT_DISCOUNT("Percentage Discount"),
    FLAT_SCHEME("Bonus / Free Goods Scheme"),
    BULK_SLAB("Bulk Volume Slab"),
    NEW_LAUNCH("New Launch Offer"),
    SEASONAL_PROMO("Seasonal Trade Offer");

    companion object {
        fun fromString(raw: String?): BannerOfferType =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: PERCENT_DISCOUNT
    }
}

enum class BannerTargetAudience(val displayName: String) {
    ALL_STOCKISTS("All Stockists"),
    VERIFIED_STOCKISTS("Verified Stockists Only"),
    HIGH_VOLUME_STOCKISTS("High-Volume Stockists");

    companion object {
        fun fromString(raw: String?): BannerTargetAudience =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: ALL_STOCKISTS
    }
}

/**
 * Manufacturer Offer Banner with all 16 enterprise specification fields.
 */
data class OfferBanner(
    val id: Long = 0L,
    val organizationId: Long,
    val organizationName: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String? = null,
    val offerType: BannerOfferType = BannerOfferType.PERCENT_DISCOUNT,
    val discountOrOfferSummary: String,
    val linkedProductId: Long? = null,
    val linkedProductName: String? = null,
    val linkedCategory: String? = null,
    val linkedCampaignId: Long? = null,
    val startDateEpochMillis: Long,
    val endDateEpochMillis: Long,
    val priorityOrder: Int = 1,
    val status: BannerStatus = BannerStatus.DRAFT,
    val targetAudience: BannerTargetAudience = BannerTargetAudience.ALL_STOCKISTS,
    val ctaText: String = "View Offer",
    val ctaDestinationRoute: String = "",
    val createdByUserId: Long,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val updatedAtEpochMillis: Long = System.currentTimeMillis()
) {
    fun effectiveStatus(nowMillis: Long = System.currentTimeMillis()): BannerStatus {
        return when (status) {
            BannerStatus.ARCHIVED -> BannerStatus.ARCHIVED
            BannerStatus.INACTIVE -> BannerStatus.INACTIVE
            BannerStatus.DRAFT -> BannerStatus.DRAFT
            BannerStatus.EXPIRED -> BannerStatus.EXPIRED
            BannerStatus.SCHEDULED, BannerStatus.ACTIVE -> when {
                nowMillis > endDateEpochMillis -> BannerStatus.EXPIRED
                nowMillis < startDateEpochMillis -> BannerStatus.SCHEDULED
                else -> BannerStatus.ACTIVE
            }
        }
    }

    fun isEligibleForStockist(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return effectiveStatus(nowMillis) == BannerStatus.ACTIVE
    }
}

enum class CampaignStatus(val displayName: String) {
    DRAFT("Draft"),
    ACTIVE("Active"),
    PAUSED("Paused"),
    EXPIRED("Expired"),
    ARCHIVED("Archived");

    companion object {
        fun fromString(raw: String?): CampaignStatus =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: DRAFT
    }
}

data class PromotionalCampaign(
    val id: Long = 0L,
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
    val status: CampaignStatus = CampaignStatus.ACTIVE,
    val redemptionsCount: Int = 0,
    val createdByUserId: Long,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
) {
    fun isCurrentlyActive(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return status == CampaignStatus.ACTIVE && nowMillis in startDateEpochMillis..endDateEpochMillis
    }
}

enum class AnnouncementCategory(val displayName: String) {
    NEW_LAUNCH("New Launch"),
    RESTOCK_UPDATE("Restock Update"),
    FORMULATION_UPDATE("Formulation / Pack Update"),
    PRICE_TRADE_ALERT("Trade / Pricing Alert"),
    COMPLIANCE_NOTICE("Regulatory Notice");

    companion object {
        fun fromString(raw: String?): AnnouncementCategory =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: NEW_LAUNCH
    }
}

enum class AnnouncementStatus(val displayName: String) {
    DRAFT("Draft"),
    PUBLISHED("Published"),
    ARCHIVED("Archived");

    companion object {
        fun fromString(raw: String?): AnnouncementStatus =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: DRAFT
    }
}

data class MedicineAnnouncement(
    val id: Long = 0L,
    val organizationId: Long,
    val organizationName: String,
    val linkedProductId: Long,
    val linkedProductName: String,
    val title: String,
    val body: String,
    val category: AnnouncementCategory = AnnouncementCategory.NEW_LAUNCH,
    val imageUrl: String? = null,
    val status: AnnouncementStatus = AnnouncementStatus.DRAFT,
    val createdByUserId: Long,
    val publishedAtEpochMillis: Long? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)

// =====================================================================
// Stockist Purchased Inventory (Strictly Separate from Manufacturer Inventory)
// =====================================================================

data class StockistPurchasedInventoryItem(
    val id: Long = 0L,
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
) {
    val isLowStock: Boolean
        get() = remainingQuantity <= reorderLevel
}

// =====================================================================
// Workspace Dashboard Summaries
// =====================================================================

data class ManufacturerDashboardSummary(
    val organizationName: String,
    val activeBannersCount: Int = 0,
    val scheduledBannersCount: Int = 0,
    val activeCampaignsCount: Int = 0,
    val publishedAnnouncementsCount: Int = 0,
    val approvedProductsCount: Int = 0,
    val pendingSubmissionsCount: Int = 0,
    val rejectedSubmissionsCount: Int = 0,
    val incomingOrdersCount: Int = 0
)

data class CompanyDashboardSummary(
    val organizationName: String,
    val totalCatalogueCount: Int = 0,
    val activeCatalogueCount: Int = 0,
    val pendingApprovalsCount: Int = 0,
    val rejectedSubmissionsCount: Int = 0,
    val masterDataEntriesCount: Int = 0,
    val organizationMembersCount: Int = 0
)
