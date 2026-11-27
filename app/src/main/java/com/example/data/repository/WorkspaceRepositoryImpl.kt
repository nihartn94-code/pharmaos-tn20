package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.DataStoreManager
import com.example.data.local.entity.ApprovalAuditLogEntity
import com.example.data.local.entity.MasterDataEntity
import com.example.data.local.entity.MedicineAnnouncementEntity
import com.example.data.local.entity.MedicineSubmissionEntity
import com.example.data.local.entity.NotificationEntity
import com.example.data.local.entity.OfferBannerEntity
import com.example.data.local.entity.OrganizationEntity
import com.example.data.local.entity.OrganizationMemberEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.PromotionalCampaignEntity
import com.example.data.local.entity.StockistPurchasedInventoryEntity
import com.example.data.local.entity.UserEntity
import com.example.data.remote.config.AuthExecutionMode
import com.example.data.remote.dto.MasterDataDto
import com.example.data.remote.mapper.toDomain
import com.example.data.remote.mapper.toDto
import com.example.data.remote.mapper.toEntity
import com.example.data.remote.source.WorkspaceRemoteDataSource
import com.example.data.remote.upload.MediaUploadManager
import com.example.data.remote.util.NetworkResult
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import com.example.domain.model.AccessControlPolicy
import com.example.domain.model.AnnouncementCategory
import com.example.domain.model.AnnouncementStatus
import com.example.domain.model.ApprovalAuditEntry
import com.example.domain.model.AuthUser
import com.example.domain.model.BannerOfferType
import com.example.domain.model.BannerStatus
import com.example.domain.model.BannerTargetAudience
import com.example.domain.model.CampaignStatus
import com.example.domain.model.CataloguePublicationStatus
import com.example.domain.model.CompanyDashboardSummary
import com.example.domain.model.ManufacturerDashboardSummary
import com.example.domain.model.MasterDataItem
import com.example.domain.model.MasterDataType
import com.example.domain.model.MedicineAnnouncement
import com.example.domain.model.MedicineDuplicateCandidate
import com.example.domain.model.MedicineSubmission
import com.example.domain.model.MedicineSubmissionStatus
import com.example.domain.model.NotificationType
import com.example.domain.model.OfferBanner
import com.example.domain.model.OrganizationMember
import com.example.domain.model.OrganizationType
import com.example.domain.model.PharmaPermission
import com.example.domain.model.PromotionalCampaign
import com.example.domain.model.RolePermissionMatrix
import com.example.domain.model.StockistPurchasedInventoryItem
import com.example.domain.model.SyncHealthStatus
import com.example.domain.model.SyncStepResult
import com.example.domain.model.UserRole
import com.example.domain.model.WorkspaceSyncState
import com.example.domain.model.WorkspaceType
import com.example.domain.repository.PharmaRepository
import com.example.domain.repository.WorkspaceRepository
import com.example.util.PasswordHasher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceRepositoryImpl(
    private val db: AppDatabase,
    private val dataStoreManager: DataStoreManager,
    private val pharmaRepository: PharmaRepository,
    private val remoteDataSource: WorkspaceRemoteDataSource? = null
) : WorkspaceRepository {

    private val workspaceDao = db.workspaceManagementDao()
    private val productDao = db.productDao()
    private val userDao = db.userDao()
    private val orderDao = db.orderDao()
    private val notificationDao = db.notificationDao()
    private val seedMutex = Mutex()
    /**
     * Operation-scoped idempotency key store: retries of the same logical mutation reuse the
     * exact same Idempotency-Key header until the operation reaches a terminal success or
     * non-retryable validation error.
     */
    private val operationIdempotencyKeys = ConcurrentHashMap<String, String>()
    private val bannerClockMillisFlow = MutableStateFlow(System.currentTimeMillis())
    private val _workspaceSyncStateFlow = MutableStateFlow(WorkspaceSyncState())
    override val workspaceSyncStateFlow: StateFlow<WorkspaceSyncState> = _workspaceSyncStateFlow.asStateFlow()
    private val activeSyncJobRef = AtomicReference<Job?>(null)

    override fun refreshBannerClock(nowEpochMillis: Long) {
        bannerClockMillisFlow.value = nowEpochMillis
    }

    override fun cancelActiveSync() {
        activeSyncJobRef.getAndSet(null)?.cancel()
    }

    internal fun obtainIdempotencyKey(operationScope: String): String =
        operationIdempotencyKeys.computeIfAbsent(operationScope) {
            "$operationScope-${UUID.randomUUID()}"
        }

    internal fun releaseIdempotencyKey(operationScope: String) {
        operationIdempotencyKeys.remove(operationScope)
    }

    private suspend fun isProductionHttpMode(): Boolean =
        dataStoreManager.authExecutionModeFlow.first() == AuthExecutionMode.PRODUCTION_BACKEND_HTTP

    private suspend fun requireAuthenticatedUser(): AuthUser =
        pharmaRepository.getApiUsersMe()
            ?: throw SecurityException("Authentication required. Please sign in to continue.")

    private suspend fun requirePermission(permission: PharmaPermission): AuthUser {
        val user = requireAuthenticatedUser()
        if (!user.hasValidOrganizationContext()) {
            throw SecurityException("Access denied: Valid organization context is missing for this account.")
        }
        if (!user.hasPermission(permission)) {
            throw SecurityException("Access denied: Missing required permission ${permission.name}.")
        }
        return user
    }

    override fun getActiveWorkspaceFlow(): Flow<WorkspaceType> {
        return combine(
            pharmaRepository.getApiUsersMeFlow(),
            dataStoreManager.activeWorkspaceFlow
        ) { user, preferred ->
            if (user == null) {
                WorkspaceType.STOCKIST
            } else {
                val allowed = user.authorizedWorkspaces()
                if (allowed.isEmpty()) {
                    WorkspaceType.STOCKIST
                } else if (preferred != null && allowed.contains(preferred)) {
                    preferred
                } else {
                    user.defaultWorkspace().takeIf { allowed.contains(it) } ?: allowed.first()
                }
            }
        }
    }

    override suspend fun switchActiveWorkspace(target: WorkspaceType): Result<WorkspaceType> {
        return runCatching {
            val user = requireAuthenticatedUser()
            if (!AccessControlPolicy.canAccessWorkspace(user, target)) {
                throw SecurityException(
                    "Access denied: Your account does not hold permissions for ${target.displayName}."
                )
            }
            dataStoreManager.setActiveWorkspace(target)
            target
        }
    }

    override suspend fun ensureWorkspaceSeededForLocalDev() {
        if (isProductionHttpMode()) return
        seedMutex.withLock {
            val now = System.currentTimeMillis()
            val dayMs = 24L * 60L * 60L * 1000L

            // 1. Seed Unified Pharmaceutical Organization & Stockist Organization
            val existingOrgs = workspaceDao.getOrganizationsByType(OrganizationType.PHARMACEUTICAL_ORGANIZATION.name)
            val pharmaOrgId = if (existingOrgs.isEmpty()) {
                workspaceDao.upsertOrganization(
                    OrganizationEntity(
                        id = 1001L,
                        name = "Bharat Bio-Pharma Formulations Pvt. Ltd.",
                        type = OrganizationType.PHARMACEUTICAL_ORGANIZATION.name,
                        gstin = "27AAACB1234F1Z9",
                        drugLicenseNumber = "MH-PAL-25B-2026-0091",
                        address = "Plant 1, Plot 88, MIDC Tarapur Industrial Area, Palghar, Maharashtra"
                    )
                )
            } else {
                existingOrgs.first().id
            }

            val existingStockistOrgs = workspaceDao.getOrganizationsByType(OrganizationType.STOCKIST.name)
            val stockistOrgId = if (existingStockistOrgs.isEmpty()) {
                workspaceDao.upsertOrganization(
                    OrganizationEntity(
                        id = 2001L,
                        name = "Apex Pharma Distributors",
                        type = OrganizationType.STOCKIST.name,
                        gstin = "27AABCA9988K1Z2",
                        drugLicenseNumber = "MH-MUM-20B-2026-4410",
                        address = "Shop 12, Medical Market, Andheri East, Mumbai, MH"
                    )
                )
            } else {
                existingStockistOrgs.first().id
            }

            // 2. Ensure dev accounts share unified Pharmaceutical Organization ID
            val mfgUser = ensureLocalDevUser(
                email = "manufacturer@pharmaos.com",
                fullName = "Dr. Vikram Malhotra (VP Manufacturing)",
                businessName = "Bharat Bio-Pharma Formulations Pvt. Ltd.",
                phone = "9876543220",
                gstin = "27AAACB1234F1Z9",
                address = "Plant 1, MIDC Tarapur, Palghar, MH",
                primaryRole = UserRole.MANUFACTURER,
                assignedRoles = setOf(UserRole.MANUFACTURER),
                organizationId = pharmaOrgId,
                organizationType = OrganizationType.PHARMACEUTICAL_ORGANIZATION
            )

            val companyUser = ensureLocalDevUser(
                email = "company@pharmaos.com",
                fullName = "Ananya Deshmukh (Catalogue & Approvals Head)",
                businessName = "Bharat Bio-Pharma Formulations Pvt. Ltd.",
                phone = "9876543221",
                gstin = "27AAACB1234F1Z9",
                address = "Corporate HQ, BKC, Mumbai, MH",
                primaryRole = UserRole.PHARMA_COMPANY,
                assignedRoles = setOf(UserRole.PHARMA_COMPANY),
                organizationId = pharmaOrgId,
                organizationType = OrganizationType.PHARMACEUTICAL_ORGANIZATION,
                explicitExtraPermissions = RolePermissionMatrix.companyOrgAdminPermissions
            )

            val dualUser = ensureLocalDevUser(
                email = "dual.lead@pharmaos.com",
                fullName = "Rajeev Menon (Plant & Brand Operations Director)",
                businessName = "Bharat Bio-Pharma Formulations Pvt. Ltd.",
                phone = "9876543222",
                gstin = "27AAACB1234F1Z9",
                address = "Corporate HQ & Plant 1, Mumbai, MH",
                primaryRole = UserRole.MANUFACTURER,
                assignedRoles = setOf(UserRole.MANUFACTURER, UserRole.PHARMA_COMPANY),
                organizationId = pharmaOrgId,
                organizationType = OrganizationType.PHARMACEUTICAL_ORGANIZATION
            )

            val stockistUser = ensureLocalDevUser(
                email = "stockist@pharmaos.com",
                fullName = "Rohan Sharma",
                businessName = "Apex Pharma Distributors",
                phone = "9876543210",
                gstin = "27AABCA9988K1Z2",
                address = "Shop 12, Medical Market, Andheri East, Mumbai, MH",
                primaryRole = UserRole.STOCKIST,
                assignedRoles = setOf(UserRole.STOCKIST),
                organizationId = stockistOrgId,
                organizationType = OrganizationType.STOCKIST
            )

            // Sync organization members for Bharat Bio-Pharma
            listOf(mfgUser, companyUser, dualUser).forEach { u ->
                val roles = RolePermissionMatrix.parseRolesCsv(u.assignedRolesCsv, UserRole.fromString(u.role))
                val explicitPerms = PharmaPermission.parseCsv(u.permissionsCsv)
                val perms = if (explicitPerms.isNotEmpty()) explicitPerms else RolePermissionMatrix.resolvePermissions(roles)
                workspaceDao.upsertMember(
                    OrganizationMemberEntity(
                        id = u.id,
                        organizationId = pharmaOrgId,
                        userId = u.id,
                        fullName = u.fullName,
                        email = u.email,
                        rolesCsv = RolePermissionMatrix.rolesToCsv(roles),
                        permissionsCsv = PharmaPermission.toCsv(perms)
                    )
                )
            }

            // Link existing seeded catalogue products to pharmaOrgId if unassigned
            pharmaRepository.ensureDefaultCatalogueSeeded()
            val allProducts = productDao.getAllProducts()
            allProducts.forEach { prod ->
                if (prod.organizationId == 0L) {
                    productDao.updateProduct(
                        prod.copy(
                            organizationId = pharmaOrgId,
                            approvalStatus = MedicineSubmissionStatus.APPROVED.name,
                            catalogueStatus = if (prod.isActive) CataloguePublicationStatus.ACTIVE.name else CataloguePublicationStatus.INACTIVE.name,
                            genericName = prod.genericName.ifBlank { prod.saltComposition.substringBefore("+").trim() },
                            skuCode = prod.skuCode.ifBlank { "SKU-${prod.id.toString().padStart(4, '0')}" },
                            mrp = if (prod.mrp > 0.0) prod.mrp else 145.0
                        )
                    )
                }
            }
            val refreshedProducts = productDao.getAllProducts()
            val firstApproved = refreshedProducts.firstOrNull {
                it.organizationId == pharmaOrgId && it.approvalStatus == MedicineSubmissionStatus.APPROVED.name
            }
            val secondApproved = refreshedProducts.drop(1).firstOrNull {
                it.organizationId == pharmaOrgId && it.approvalStatus == MedicineSubmissionStatus.APPROVED.name
            }

            // 3. Seed Master Data
            if (workspaceDao.getMasterDataCount() == 0) {
                val defaults = listOf(
                    MasterDataEntity(organizationId = 0L, type = MasterDataType.SALT_COMPOSITION.name, name = "Paracetamol", normalizedName = "paracetamol", code = "SALT-PCM", description = "Analgesic & Antipyretic"),
                    MasterDataEntity(organizationId = 0L, type = MasterDataType.SALT_COMPOSITION.name, name = "Amoxicillin + Clavulanic Acid", normalizedName = "amoxicillin + clavulanic acid", code = "SALT-AMXCLV", description = "Broad-spectrum beta-lactam antibiotic"),
                    MasterDataEntity(organizationId = 0L, type = MasterDataType.SALT_COMPOSITION.name, name = "Azithromycin", normalizedName = "azithromycin", code = "SALT-AZM", description = "Macrolide antibiotic"),
                    MasterDataEntity(organizationId = 0L, type = MasterDataType.GENERIC_MEDICINE.name, name = "Acetaminophen Oral Solid", normalizedName = "acetaminophen oral solid", code = "GEN-001", description = "Standard antipyretic generic"),
                    MasterDataEntity(organizationId = 0L, type = MasterDataType.DOSAGE_FORM.name, name = "Tablet", normalizedName = "tablet", code = "DF-TAB", description = "Compressed oral solid dosage"),
                    MasterDataEntity(organizationId = 0L, type = MasterDataType.DOSAGE_FORM.name, name = "Capsule", normalizedName = "capsule", code = "DF-CAP", description = "Gelatin oral capsule"),
                    MasterDataEntity(organizationId = 0L, type = MasterDataType.DOSAGE_FORM.name, name = "Syrup", normalizedName = "syrup", code = "DF-SYR", description = "Oral liquid suspension/syrup"),
                    MasterDataEntity(organizationId = 0L, type = MasterDataType.STRENGTH.name, name = "500 mg", normalizedName = "500 mg", code = "STR-500MG", description = "Standard adult strength"),
                    MasterDataEntity(organizationId = 0L, type = MasterDataType.STRENGTH.name, name = "650 mg", normalizedName = "650 mg", code = "STR-650MG", description = "High-strength antipyretic"),
                    MasterDataEntity(organizationId = pharmaOrgId, type = MasterDataType.BRAND.name, name = "BioCare Formulations", normalizedName = "biocare formulations", code = "BRD-BIO", description = "Primary ethical brand line", isOrganizationScoped = true),
                    MasterDataEntity(organizationId = 0L, type = MasterDataType.PACKAGING_TYPE.name, name = "Alu-Alu Blister Strip", normalizedName = "alu-alu blister strip", code = "PKG-ALU", description = "Moisture-barrier pharmaceutical strip"),
                    MasterDataEntity(organizationId = 0L, type = MasterDataType.PRODUCT_CLASSIFICATION.name, name = "Schedule H (Rx)", normalizedName = "schedule h (rx)", code = "CLS-SCH-H", description = "Prescription-only regulated medicine")
                )
                defaults.forEach { workspaceDao.insertMasterDataIgnoreDuplicate(it) }
            }

            // 4. Seed Pending Medicine Submission for Approval Queue demo
            if (workspaceDao.getSubmissionsByOrganization(pharmaOrgId).isEmpty()) {
                val pendingId = workspaceDao.insertSubmission(
                    MedicineSubmissionEntity(
                        organizationId = pharmaOrgId,
                        organizationName = "Bharat Bio-Pharma Formulations Pvt. Ltd.",
                        submittedByUserId = mfgUser.id,
                        submittedByName = mfgUser.fullName,
                        submittedByRole = UserRole.MANUFACTURER.name,
                        medicineName = "Cefixime Trihydrate 200mg DT",
                        brandName = "BioCef-200 DT",
                        genericName = "Cefixime Dispersible Tablet",
                        saltComposition = "Cefixime 200 mg",
                        dosageForm = "Tablet",
                        strength = "200 mg",
                        routeOfAdministration = "Oral",
                        packSize = "10 x 10 Tablets",
                        packagingType = "Alu-Alu Blister Strip",
                        therapeuticCategory = "Anti-Infectives",
                        storageCondition = "ROOM_TEMPERATURE",
                        isRx = true,
                        mrp = 185.0,
                        wholesaleRatePtr = 124.50,
                        hsnCode = "30042019",
                        gstRatePercent = 12.0,
                        skuCode = "BBP-CEF200-DT",
                        description = "Third-generation cephalosporin dispersible tablet formulated at WHO-GMP Tarapur Plant 1.",
                        regulatoryIdentifier = "CDSCO-MFG-2026-8841",
                        approvalStatus = MedicineSubmissionStatus.PENDING_APPROVAL.name,
                        catalogueStatus = CataloguePublicationStatus.INACTIVE.name,
                        submittedAtEpochMillis = now - (2 * dayMs),
                        updatedAtEpochMillis = now - (2 * dayMs)
                    )
                )
                workspaceDao.insertAuditLog(
                    ApprovalAuditLogEntity(
                        submissionId = pendingId,
                        organizationId = pharmaOrgId,
                        action = "SUBMITTED",
                        fromStatus = MedicineSubmissionStatus.DRAFT.name,
                        toStatus = MedicineSubmissionStatus.PENDING_APPROVAL.name,
                        actorUserId = mfgUser.id,
                        actorName = mfgUser.fullName,
                        notes = "Submitted new WHO-GMP batch formulation for Company Catalogue review.",
                        timestampEpochMillis = now - (2 * dayMs)
                    )
                )
            }

            // 5. Seed Offer Banners (1 ACTIVE, 1 SCHEDULED, 1 EXPIRED to verify Stockist filtering)
            if (workspaceDao.getBannerCount() == 0) {
                workspaceDao.insertBanner(
                    OfferBannerEntity(
                        organizationId = pharmaOrgId,
                        organizationName = "Bharat Bio-Pharma Formulations Pvt. Ltd.",
                        title = "Monsoon Trade Slab: Extra 12% PTR Off",
                        subtitle = "Direct factory dispatch on verified anti-infectives & antipyretics.",
                        offerType = BannerOfferType.BULK_SLAB.name,
                        discountOrOfferSummary = "12% Extra Trade Discount + Free Freight on 25+ Boxes",
                        linkedProductId = firstApproved?.id,
                        linkedProductName = firstApproved?.name,
                        linkedCategory = firstApproved?.therapeuticCategory ?: "Analgesics",
                        startDateEpochMillis = now - dayMs,
                        endDateEpochMillis = now + (21 * dayMs),
                        priorityOrder = 1,
                        status = BannerStatus.ACTIVE.name,
                        targetAudience = BannerTargetAudience.ALL_STOCKISTS.name,
                        ctaText = "Explore Offer",
                        ctaDestinationRoute = firstApproved?.let { "medicine_details/${it.id}" } ?: "medicine_catalogue",
                        createdByUserId = mfgUser.id
                    )
                )
                workspaceDao.insertBanner(
                    OfferBannerEntity(
                        organizationId = pharmaOrgId,
                        organizationName = "Bharat Bio-Pharma Formulations Pvt. Ltd.",
                        title = "Upcoming Q4 Bonus Scheme (10+2 Free)",
                        subtitle = "Scheduled festival procurement scheme for high-volume distributors.",
                        offerType = BannerOfferType.FLAT_SCHEME.name,
                        discountOrOfferSummary = "Buy 10 Boxes Get 2 Free",
                        linkedProductId = secondApproved?.id,
                        linkedProductName = secondApproved?.name,
                        linkedCategory = secondApproved?.therapeuticCategory,
                        startDateEpochMillis = now + (5 * dayMs),
                        endDateEpochMillis = now + (30 * dayMs),
                        priorityOrder = 2,
                        status = BannerStatus.SCHEDULED.name,
                        targetAudience = BannerTargetAudience.VERIFIED_STOCKISTS.name,
                        ctaText = "Preview Scheme",
                        ctaDestinationRoute = "medicine_catalogue",
                        createdByUserId = mfgUser.id
                    )
                )
                workspaceDao.insertBanner(
                    OfferBannerEntity(
                        organizationId = pharmaOrgId,
                        organizationName = "Bharat Bio-Pharma Formulations Pvt. Ltd.",
                        title = "Expired Summer Clearance Banner",
                        subtitle = "Must never appear on Stockist dashboard because end date has passed.",
                        offerType = BannerOfferType.SEASONAL_PROMO.name,
                        discountOrOfferSummary = "Flat 8% Clearance",
                        linkedProductId = firstApproved?.id,
                        linkedProductName = firstApproved?.name,
                        startDateEpochMillis = now - (30 * dayMs),
                        endDateEpochMillis = now - (2 * dayMs),
                        priorityOrder = 5,
                        status = BannerStatus.ACTIVE.name, // intentionally ACTIVE with past endDate to prove expiry guard!
                        targetAudience = BannerTargetAudience.ALL_STOCKISTS.name,
                        ctaText = "Expired",
                        ctaDestinationRoute = "medicine_catalogue",
                        createdByUserId = mfgUser.id
                    )
                )

                // Seed Promotional Campaign
                workspaceDao.insertCampaign(
                    PromotionalCampaignEntity(
                        organizationId = pharmaOrgId,
                        organizationName = "Bharat Bio-Pharma Formulations Pvt. Ltd.",
                        name = "Q4 Essential Generics Volume Drive",
                        schemeSummary = "Tiered 8%–15% wholesale rebate on minimum 50 strip procurement",
                        discountPercent = 12.0,
                        minOrderQuantity = 50,
                        linkedProductIdsCsv = listOfNotNull(firstApproved?.id, secondApproved?.id).joinToString(","),
                        linkedProductNamesSummary = listOfNotNull(firstApproved?.name, secondApproved?.name).joinToString(" • "),
                        termsAndConditions = "Applicable on GST-invoiced orders paid within 14-day credit cycle.",
                        startDateEpochMillis = now - (2 * dayMs),
                        endDateEpochMillis = now + (25 * dayMs),
                        status = CampaignStatus.ACTIVE.name,
                        redemptionsCount = 19,
                        createdByUserId = mfgUser.id
                    )
                )

                // Seed Published Medicine Announcement
                if (firstApproved != null) {
                    workspaceDao.insertAnnouncement(
                        MedicineAnnouncementEntity(
                            organizationId = pharmaOrgId,
                            organizationName = "Bharat Bio-Pharma Formulations Pvt. Ltd.",
                            linkedProductId = firstApproved.id,
                            linkedProductName = firstApproved.name,
                            title = "Fresh WHO-GMP Batch Ready for Dispatch",
                            body = "New 24-month shelf-life stock of ${firstApproved.name} is now live with priority cold-chain safe transport.",
                            category = AnnouncementCategory.RESTOCK_UPDATE.name,
                            status = AnnouncementStatus.PUBLISHED.name,
                            createdByUserId = mfgUser.id,
                            publishedAtEpochMillis = now - (6 * 60 * 60 * 1000L)
                        )
                    )
                }

                // Seed Stockist Purchased Inventory (Strictly isolated under stockistOrgId!)
                if (firstApproved != null) {
                    workspaceDao.upsertStockistInventoryItem(
                        StockistPurchasedInventoryEntity(
                            stockistOrganizationId = stockistOrgId,
                            stockistUserId = stockistUser.id,
                            productId = firstApproved.id,
                            productName = firstApproved.name,
                            brand = firstApproved.brand,
                            saltComposition = firstApproved.saltComposition,
                            batchNumber = "STK-PUR-2026A",
                            expiryDateEpochMillis = now + (240 * dayMs),
                            purchasedQuantity = 120,
                            remainingQuantity = 78,
                            reorderLevel = 30,
                            purchaseUnitPrice = 24.50,
                            sourceOrderNumber = "PO-2026-1004"
                        )
                    )
                }
            }
        }
    }

    private suspend fun ensureLocalDevUser(
        email: String,
        fullName: String,
        businessName: String,
        phone: String,
        gstin: String,
        address: String,
        primaryRole: UserRole,
        assignedRoles: Set<UserRole>,
        organizationId: Long,
        organizationType: OrganizationType,
        explicitExtraPermissions: Set<PharmaPermission> = emptySet()
    ): UserEntity {
        val perms = RolePermissionMatrix.resolvePermissions(assignedRoles) + explicitExtraPermissions
        val existing = userDao.getUserByEmail(email.lowercase())
        if (existing != null) {
            val updated = existing.copy(
                organizationId = organizationId,
                organizationType = organizationType.name,
                organizationName = businessName,
                assignedRolesCsv = RolePermissionMatrix.rolesToCsv(assignedRoles),
                permissionsCsv = PharmaPermission.toCsv(perms)
            )
            userDao.updateUser(updated)
            return updated
        }
        val hashed = PasswordHasher.hashPassword("Password@123")
        val entity = UserEntity(
            fullName = fullName,
            businessName = businessName,
            email = email.lowercase(),
            phone = phone,
            gstin = gstin,
            address = address,
            passwordHash = hashed.hashHex,
            passwordSalt = hashed.saltHex,
            role = primaryRole.name,
            status = "ACTIVE",
            emailVerified = true,
            phoneVerified = true,
            organizationId = organizationId,
            organizationType = organizationType.name,
            organizationName = businessName,
            assignedRolesCsv = RolePermissionMatrix.rolesToCsv(assignedRoles),
            permissionsCsv = PharmaPermission.toCsv(perms)
        )
        val id = userDao.insertUser(entity)
        return entity.copy(id = id)
    }

    override fun getOrganizationMembersFlow(): Flow<List<OrganizationMember>> {
        return pharmaRepository.getApiUsersMeFlow().flatMapLatest { user ->
            if (user == null || user.organizationId <= 0L) {
                flowOf(emptyList())
            } else {
                workspaceDao.getOrganizationMembersFlow(user.organizationId).map { list ->
                    list.map { it.toDomain() }
                }
            }
        }
    }

    override suspend fun assignOrganizationMemberRoles(
        memberId: Long,
        roles: Set<UserRole>
    ): Result<OrganizationMember> {
        return runCatching {
            val actor = requireAuthenticatedUser()
            AccessControlPolicy.canManageOrganizationRoles(actor, actor.organizationId).getOrThrow()
            if (roles.isEmpty()) throw IllegalArgumentException("At least one role must be assigned.")
            if (actor.organizationType == OrganizationType.PHARMACEUTICAL_ORGANIZATION && roles.contains(UserRole.STOCKIST)) {
                throw SecurityException("Stockist role cannot be assigned inside a Pharmaceutical Organization.")
            }
            if (roles.contains(UserRole.ADMIN) && actor.role != UserRole.ADMIN) {
                throw SecurityException("Platform Admin role cannot be assigned by organization administrators.")
            }
            if (isProductionHttpMode()) {
                val remote = remoteDataSource
                    ?: throw IllegalStateException("Remote workspace API is not initialized.")
                when (val res = remote.assignMemberRoles(actor.organizationId, memberId, roles.map { it.name })) {
                    is NetworkResult.Success -> {
                        val entity = res.data.toEntity(isProductionMode = true)
                        workspaceDao.upsertMember(entity)
                        return@runCatching entity.toDomain(isProductionMode = true)
                    }
                    else -> throw res.toException()
                }
            }
            val members = workspaceDao.getOrganizationMembers(actor.organizationId)
            val target = members.firstOrNull { it.id == memberId }
                ?: throw NoSuchElementException("Organization member not found.")
            if (!AccessControlPolicy.canOperateOnOrganization(actor, target.organizationId)) {
                throw SecurityException("Cross-organization member role modification is forbidden.")
            }
            val perms = RolePermissionMatrix.resolvePermissions(roles)
            val updated = target.copy(
                rolesCsv = RolePermissionMatrix.rolesToCsv(roles),
                permissionsCsv = PharmaPermission.toCsv(perms)
            )
            workspaceDao.upsertMember(updated)
            userDao.getUserById(target.userId)?.let { u ->
                userDao.updateUser(
                    u.copy(
                        assignedRolesCsv = updated.rolesCsv,
                        permissionsCsv = updated.permissionsCsv
                    )
                )
            }
            updated.toDomain()
        }
    }

    override fun getManufacturerDashboardSummaryFlow(): Flow<ManufacturerDashboardSummary> {
        return pharmaRepository.getApiUsersMeFlow().flatMapLatest { user ->
            if (user == null || !AccessControlPolicy.canAccessWorkspace(user, WorkspaceType.MANUFACTURER)) {
                flowOf(ManufacturerDashboardSummary(organizationName = user?.organizationName ?: ""))
            } else {
                combine(
                    workspaceDao.getBannersByOrganizationFlow(user.organizationId),
                    workspaceDao.getCampaignsByOrganizationFlow(user.organizationId),
                    workspaceDao.getAnnouncementsByOrganizationFlow(user.organizationId),
                    workspaceDao.getSubmissionsByOrganizationFlow(user.organizationId),
                    productDao.getAllProductsFlow()
                ) { banners, campaigns, announcements, submissions, products ->
                    val now = System.currentTimeMillis()
                    val domainBanners = banners.map { it.toDomain() }
                    val approvedOrgProducts = products.count {
                        (it.organizationId == user.organizationId || user.role == UserRole.ADMIN) &&
                            it.approvalStatus == MedicineSubmissionStatus.APPROVED.name &&
                            it.catalogueStatus == CataloguePublicationStatus.ACTIVE.name
                    }
                    ManufacturerDashboardSummary(
                        organizationName = user.organizationName,
                        activeBannersCount = domainBanners.count { it.effectiveStatus(now) == BannerStatus.ACTIVE },
                        scheduledBannersCount = domainBanners.count { it.effectiveStatus(now) == BannerStatus.SCHEDULED },
                        activeCampaignsCount = campaigns.count { it.toDomain().isCurrentlyActive(now) },
                        publishedAnnouncementsCount = announcements.count { it.status == AnnouncementStatus.PUBLISHED.name },
                        approvedProductsCount = approvedOrgProducts,
                        pendingSubmissionsCount = submissions.count { it.approvalStatus == MedicineSubmissionStatus.PENDING_APPROVAL.name },
                        rejectedSubmissionsCount = submissions.count { it.approvalStatus == MedicineSubmissionStatus.REJECTED.name },
                        incomingOrdersCount = orderDao.getOrdersForSupplier(user.id).count { it.status == "PLACED" || it.status == "ACCEPTED" }
                    )
                }
            }
        }
    }

    override fun getCompanyDashboardSummaryFlow(): Flow<CompanyDashboardSummary> {
        return pharmaRepository.getApiUsersMeFlow().flatMapLatest { user ->
            if (user == null || !AccessControlPolicy.canAccessWorkspace(user, WorkspaceType.COMPANY)) {
                flowOf(CompanyDashboardSummary(organizationName = user?.organizationName ?: ""))
            } else {
                combine(
                    productDao.getAllProductsFlow(),
                    workspaceDao.getSubmissionsByOrganizationFlow(user.organizationId),
                    workspaceDao.getMasterDataForOrganizationFlow(user.organizationId),
                    workspaceDao.getOrganizationMembersFlow(user.organizationId)
                ) { products, submissions, masterData, members ->
                    val orgProducts = products.filter {
                        it.organizationId == user.organizationId || user.role == UserRole.ADMIN
                    }
                    CompanyDashboardSummary(
                        organizationName = user.organizationName,
                        totalCatalogueCount = orgProducts.size,
                        activeCatalogueCount = orgProducts.count {
                            it.approvalStatus == MedicineSubmissionStatus.APPROVED.name &&
                                it.catalogueStatus == CataloguePublicationStatus.ACTIVE.name &&
                                it.isActive
                        },
                        pendingApprovalsCount = submissions.count { it.approvalStatus == MedicineSubmissionStatus.PENDING_APPROVAL.name },
                        rejectedSubmissionsCount = submissions.count { it.approvalStatus == MedicineSubmissionStatus.REJECTED.name },
                        masterDataEntriesCount = masterData.size,
                        organizationMembersCount = members.size
                    )
                }
            }
        }
    }

    // =========================================================================
    // Compulsory Medicine Approval Workflow
    // =========================================================================

    override fun getOrganizationSubmissionsFlow(): Flow<List<MedicineSubmission>> {
        return pharmaRepository.getApiUsersMeFlow().flatMapLatest { user ->
            if (user == null || user.organizationType == OrganizationType.STOCKIST) {
                flowOf(emptyList())
            } else if (user.role == UserRole.ADMIN) {
                workspaceDao.getAllSubmissionsFlow().map { list -> list.map { it.toDomain() } }
            } else {
                workspaceDao.getSubmissionsByOrganizationFlow(user.organizationId).map { list ->
                    list.map { it.toDomain() }
                }
            }
        }
    }

    override fun getAuditLogsForOrganizationFlow(): Flow<List<ApprovalAuditEntry>> {
        return pharmaRepository.getApiUsersMeFlow().flatMapLatest { user ->
            if (user == null || user.organizationType == OrganizationType.STOCKIST) {
                flowOf(emptyList())
            } else {
                workspaceDao.getAuditLogsByOrganizationFlow(user.organizationId).map { list ->
                    list.map { it.toDomain() }
                }
            }
        }
    }

    override suspend fun detectDuplicateMedicines(
        medicineName: String,
        brandName: String,
        saltComposition: String,
        strength: String,
        dosageForm: String
    ): List<MedicineDuplicateCandidate> {
        val normName = medicineName.trim().lowercase()
        val normBrand = brandName.trim().lowercase()
        val normSalt = saltComposition.trim().lowercase()
        val normStrength = strength.trim().lowercase()
        val normDosage = dosageForm.trim().lowercase()

        if (normName.isBlank() && normSalt.isBlank()) return emptyList()

        return productDao.getAllProducts().mapNotNull { p ->
            val nameMatch = normName.isNotBlank() && p.name.trim().lowercase() == normName &&
                p.strength.trim().lowercase() == normStrength &&
                p.dosageForm.trim().lowercase() == normDosage
            val compositionMatch = normBrand.isNotBlank() && normSalt.isNotBlank() &&
                p.brand.trim().lowercase() == normBrand &&
                p.saltComposition.trim().lowercase() == normSalt &&
                p.strength.trim().lowercase() == normStrength

            when {
                nameMatch -> MedicineDuplicateCandidate(
                    existingProductId = p.id,
                    name = p.name,
                    brand = p.brand,
                    saltComposition = p.saltComposition,
                    strength = p.strength,
                    dosageForm = p.dosageForm,
                    matchReason = "Exact match on Medicine Name, Strength & Dosage Form"
                )
                compositionMatch -> MedicineDuplicateCandidate(
                    existingProductId = p.id,
                    name = p.name,
                    brand = p.brand,
                    saltComposition = p.saltComposition,
                    strength = p.strength,
                    dosageForm = p.dosageForm,
                    matchReason = "Duplicate Brand + Salt Composition + Strength"
                )
                else -> null
            }
        }
    }

    private fun validateMedicineSubmission(submission: MedicineSubmission, isProduction: Boolean) {
        if (submission.medicineName.isBlank()) throw IllegalArgumentException("Medicine name is required.")
        if (submission.brandName.isBlank()) throw IllegalArgumentException("Brand name is required.")
        if (submission.saltComposition.isBlank()) throw IllegalArgumentException("Salt composition is required.")
        if (submission.dosageForm.isBlank()) throw IllegalArgumentException("Dosage form is required.")
        if (submission.strength.isBlank()) throw IllegalArgumentException("Strength is required.")
        if (submission.packSize.isBlank()) throw IllegalArgumentException("Pack size is required.")
        val hsnDigits = submission.hsnCode.trim()
        if (hsnDigits.length !in 4..8 || !hsnDigits.all(Char::isDigit)) {
            throw IllegalArgumentException("Valid 4 to 8 digit HSN code is required (no silent defaults).")
        }
        val validGstRates = setOf(0.0, 5.0, 12.0, 18.0, 28.0)
        if (submission.gstRatePercent !in validGstRates) {
            throw IllegalArgumentException("Explicit GST rate is required (0%, 5%, 12%, 18%, or 28%).")
        }
        if (submission.mrp < 0.0 || submission.wholesaleRatePtr < 0.0) {
            throw IllegalArgumentException("Prices cannot be negative.")
        }
        if (submission.mrp > 0.0 && submission.wholesaleRatePtr > submission.mrp) {
            throw IllegalArgumentException("Wholesale PTR rate cannot exceed MRP.")
        }
        if (isProduction) {
            if (!MediaUploadManager.isPersistableProductionMediaUrl(submission.imageUrl)) {
                throw IllegalArgumentException("Upload medicine pack image to server before saving in production mode.")
            }
            if (!MediaUploadManager.isPersistableProductionMediaUrl(submission.supportingDocUri)) {
                throw IllegalArgumentException("Upload supporting regulatory document to server before saving in production mode.")
            }
        }
    }

    override suspend fun saveMedicineDraft(submission: MedicineSubmission): Result<MedicineSubmission> {
        return runCatching {
            val actor = requirePermission(PharmaPermission.MEDICINE_SUBMIT)
            if (actor.organizationType != OrganizationType.PHARMACEUTICAL_ORGANIZATION && actor.role != UserRole.ADMIN) {
                throw SecurityException("Stockists cannot submit medicines to the catalogue.")
            }
            if (submission.medicineName.isBlank()) {
                throw IllegalArgumentException("Enter at least a medicine name before saving draft.")
            }
            val isProd = isProductionHttpMode()
            if (isProd) {
                if (!MediaUploadManager.isPersistableProductionMediaUrl(submission.imageUrl) ||
                    !MediaUploadManager.isPersistableProductionMediaUrl(submission.supportingDocUri)
                ) {
                    throw IllegalArgumentException("Attached media must be uploaded before saving draft in production mode.")
                }
            }
            if (submission.id > 0L) {
                val existing = workspaceDao.getSubmissionById(submission.id)?.toDomain()
                if (existing != null) {
                    AccessControlPolicy.canEditMedicineSubmission(actor, existing).getOrThrow()
                }
            }
            val now = System.currentTimeMillis()
            val prepared = submission.copy(
                organizationId = actor.organizationId,
                organizationName = actor.organizationName,
                submittedByUserId = actor.id,
                submittedByName = actor.fullName,
                submittedByRole = actor.role,
                approvalStatus = MedicineSubmissionStatus.DRAFT,
                catalogueStatus = CataloguePublicationStatus.INACTIVE,
                updatedAtEpochMillis = now
            )
            if (isProd) {
                val remote = remoteDataSource
                    ?: throw IllegalStateException("Remote workspace API is not initialized.")
                val opScope = "med-draft-${actor.organizationId}-${prepared.id}-${prepared.medicineName.trim().lowercase()}"
                val res = if (prepared.id > 0L) {
                    remote.updateMedicineSubmission(prepared.id, prepared.toDto())
                } else {
                    remote.createMedicineSubmission(
                        dto = prepared.toDto(),
                        idempotencyKey = obtainIdempotencyKey(opScope)
                    )
                }
                when (res) {
                    is NetworkResult.Success -> {
                        releaseIdempotencyKey(opScope)
                        val saved = res.data.toEntity()
                        workspaceDao.insertSubmission(saved)
                        return@runCatching saved.toDomain()
                    }
                    else -> throw res.toException()
                }
            }
            val id = workspaceDao.insertSubmission(prepared.toEntity())
            prepared.copy(id = id)
        }
    }

    override suspend fun submitMedicineForApproval(submission: MedicineSubmission): Result<MedicineSubmission> {
        return runCatching {
            val actor = requirePermission(PharmaPermission.MEDICINE_SUBMIT)
            if (actor.organizationType != OrganizationType.PHARMACEUTICAL_ORGANIZATION && actor.role != UserRole.ADMIN) {
                throw SecurityException("Stockists are prohibited from submitting medicines.")
            }
            val isProd = isProductionHttpMode()
            validateMedicineSubmission(submission, isProduction = isProd)
            if (submission.id > 0L) {
                val existing = workspaceDao.getSubmissionById(submission.id)?.toDomain()
                if (existing != null) {
                    AccessControlPolicy.canEditMedicineSubmission(actor, existing).getOrThrow()
                }
            }
            val now = System.currentTimeMillis()
            val prepared = submission.copy(
                organizationId = actor.organizationId,
                organizationName = actor.organizationName,
                submittedByUserId = actor.id,
                submittedByName = actor.fullName,
                submittedByRole = actor.role,
                approvalStatus = MedicineSubmissionStatus.PENDING_APPROVAL,
                catalogueStatus = CataloguePublicationStatus.INACTIVE,
                rejectionReason = null,
                submittedAtEpochMillis = now,
                updatedAtEpochMillis = now
            )

            if (isProd) {
                val remote = remoteDataSource
                    ?: throw IllegalStateException("Remote workspace API is not initialized.")
                val createScope = "med-create-${actor.organizationId}-${prepared.medicineName.trim().lowercase()}-${prepared.strength.trim().lowercase()}"
                val submitScope = "med-submit-${actor.organizationId}-${prepared.id}-${prepared.medicineName.trim().lowercase()}"
                val submittedRes = if (prepared.id > 0L) {
                    // Always persist edited draft/rejected fields via PUT before transitioning via POST /submit
                    when (val updated = remote.updateMedicineSubmission(prepared.id, prepared.toDto())) {
                        is NetworkResult.Success -> remote.submitMedicineForApproval(
                            submissionId = updated.data.id,
                            idempotencyKey = obtainIdempotencyKey(submitScope)
                        )
                        else -> updated
                    }
                } else {
                    when (
                        val created = remote.createMedicineSubmission(
                            dto = prepared.toDto(),
                            idempotencyKey = obtainIdempotencyKey(createScope)
                        )
                    ) {
                        is NetworkResult.Success -> {
                            releaseIdempotencyKey(createScope)
                            remote.submitMedicineForApproval(
                                submissionId = created.data.id,
                                idempotencyKey = obtainIdempotencyKey("med-submit-${created.data.id}")
                            )
                        }
                        else -> created
                    }
                }
                when (submittedRes) {
                    is NetworkResult.Success -> {
                        releaseIdempotencyKey(submitScope)
                        releaseIdempotencyKey("med-submit-${submittedRes.data.id}")
                        val saved = submittedRes.data.toEntity()
                        workspaceDao.insertSubmission(saved)
                        runCatching { syncApprovalAuditLogs(saved.id) }
                        return@runCatching saved.toDomain()
                    }
                    else -> throw submittedRes.toException()
                }
            }

            val savedId = db.withTransaction {
                val id = workspaceDao.insertSubmission(prepared.toEntity())
                workspaceDao.insertAuditLog(
                    ApprovalAuditLogEntity(
                        submissionId = id,
                        organizationId = actor.organizationId,
                        action = "SUBMITTED_FOR_APPROVAL",
                        fromStatus = submission.approvalStatus.name,
                        toStatus = MedicineSubmissionStatus.PENDING_APPROVAL.name,
                        actorUserId = actor.id,
                        actorName = actor.fullName,
                        notes = "Submitted ${prepared.medicineName} (${prepared.strength}) for mandatory Company approval.",
                        timestampEpochMillis = now
                    )
                )
                id
            }

            // Notify Company approvers in the same pharmaceutical organization
            notifyCompanyApprovers(
                organizationId = actor.organizationId,
                excludeUserId = actor.id,
                title = "Medicine Approval Required",
                message = "${actor.fullName} submitted ${prepared.medicineName} (${prepared.brandName}) for catalogue approval.",
                referenceId = savedId
            )

            prepared.copy(id = savedId)
        }
    }

    override suspend fun resubmitRejectedMedicine(submission: MedicineSubmission): Result<MedicineSubmission> {
        if (submission.approvalStatus != MedicineSubmissionStatus.REJECTED &&
            submission.approvalStatus != MedicineSubmissionStatus.WITHDRAWN &&
            submission.approvalStatus != MedicineSubmissionStatus.DRAFT
        ) {
            return Result.failure(IllegalStateException("Only DRAFT, REJECTED, or WITHDRAWN submissions can be resubmitted."))
        }
        return submitMedicineForApproval(submission)
    }

    override suspend fun withdrawMedicineSubmission(submissionId: Long): Result<MedicineSubmission> {
        return runCatching {
            val actor = requirePermission(PharmaPermission.MEDICINE_SUBMIT)
            val existing = workspaceDao.getSubmissionById(submissionId)?.toDomain()
                ?: throw NoSuchElementException("Submission not found.")
            if (!AccessControlPolicy.canOperateOnOrganization(actor, existing.organizationId)) {
                throw SecurityException("Cross-organization withdrawal denied.")
            }
            if (existing.approvalStatus != MedicineSubmissionStatus.PENDING_APPROVAL) {
                throw IllegalStateException("Only PENDING_APPROVAL submissions can be withdrawn.")
            }
            if (isProductionHttpMode()) {
                val remote = remoteDataSource
                    ?: throw IllegalStateException("Remote workspace API is not initialized.")
                when (val res = remote.withdrawMedicineSubmission(submissionId)) {
                    is NetworkResult.Success -> {
                        val saved = res.data.toEntity()
                        workspaceDao.insertSubmission(saved)
                        return@runCatching saved.toDomain()
                    }
                    else -> throw res.toException()
                }
            }
            val now = System.currentTimeMillis()
            val updated = existing.copy(
                approvalStatus = MedicineSubmissionStatus.WITHDRAWN,
                updatedAtEpochMillis = now
            )
            workspaceDao.updateSubmission(updated.toEntity())
            workspaceDao.insertAuditLog(
                ApprovalAuditLogEntity(
                    submissionId = submissionId,
                    organizationId = existing.organizationId,
                    action = "WITHDRAWN",
                    fromStatus = MedicineSubmissionStatus.PENDING_APPROVAL.name,
                    toStatus = MedicineSubmissionStatus.WITHDRAWN.name,
                    actorUserId = actor.id,
                    actorName = actor.fullName,
                    notes = "Submission withdrawn by submitter before approval.",
                    timestampEpochMillis = now
                )
            )
            updated
        }
    }

    override suspend fun approveMedicineSubmission(
        submissionId: Long,
        reviewNotes: String,
        allowSelfApproval: Boolean
    ): Result<MedicineSubmission> {
        return runCatching {
            val reviewer = requireAuthenticatedUser()
            val existing = workspaceDao.getSubmissionById(submissionId)?.toDomain()
                ?: throw NoSuchElementException("Medicine submission #$submissionId not found.")

            AccessControlPolicy.canApproveSubmission(
                reviewer = reviewer,
                submission = existing,
                allowSelfApproval = allowSelfApproval
            ).getOrThrow()

            if (isProductionHttpMode()) {
                val remote = remoteDataSource
                    ?: throw IllegalStateException("Remote workspace API is not initialized.")
                val approveScope = "med-approve-$submissionId"
                when (
                    val res = remote.approveMedicineSubmission(
                        submissionId = submissionId,
                        notes = reviewNotes,
                        idempotencyKey = obtainIdempotencyKey(approveScope)
                    )
                ) {
                    is NetworkResult.Success -> {
                        releaseIdempotencyKey(approveScope)
                        val saved = res.data.toEntity()
                        workspaceDao.insertSubmission(saved)
                        // Immediately sync live approved catalogue and audit trail from backend
                        runCatching { syncCompanyCatalogue() }
                        runCatching { syncApprovalAuditLogs(submissionId) }
                        return@runCatching saved.toDomain()
                    }
                    else -> throw res.toException()
                }
            }

            if (existing.hsnCode.isBlank()) {
                throw IllegalStateException("Cannot approve submission #$submissionId without an explicit HSN code.")
            }

            val now = System.currentTimeMillis()
            val approvedResult = db.withTransaction {
                val productEntity = ProductEntity(
                    id = existing.approvedProductId ?: 0L,
                    name = existing.medicineName,
                    brand = existing.brandName,
                    saltComposition = existing.saltComposition,
                    strength = existing.strength,
                    dosageForm = existing.dosageForm,
                    therapeuticCategory = existing.therapeuticCategory.ifBlank { "General Medicine" },
                    manufacturerName = existing.organizationName,
                    packSize = existing.packSize,
                    hsnCode = existing.hsnCode.trim(),
                    gstRatePercent = existing.gstRatePercent,
                    storageCondition = existing.storageCondition,
                    isRx = existing.isRx,
                    isActive = true,
                    createdBy = existing.submittedByUserId,
                    createdAtEpochMillis = now,
                    organizationId = existing.organizationId,
                    approvalStatus = MedicineSubmissionStatus.APPROVED.name,
                    catalogueStatus = CataloguePublicationStatus.ACTIVE.name,
                    genericName = existing.genericName.ifBlank { existing.saltComposition },
                    routeOfAdministration = existing.routeOfAdministration,
                    packagingType = existing.packagingType,
                    description = existing.description,
                    imageUrl = existing.imageUrl,
                    skuCode = existing.skuCode.ifBlank { "MED-${existing.id}-${now % 10000}" },
                    mrp = existing.mrp,
                    regulatoryIdentifier = existing.regulatoryIdentifier
                )
                val productId = productDao.insertProduct(productEntity)
                val updated = existing.copy(
                    approvalStatus = MedicineSubmissionStatus.APPROVED,
                    catalogueStatus = CataloguePublicationStatus.ACTIVE,
                    approvedProductId = productId,
                    reviewedByUserId = reviewer.id,
                    reviewedByName = reviewer.fullName,
                    reviewNotes = reviewNotes.trim().ifBlank { "Approved for active B2B catalogue." },
                    rejectionReason = null,
                    reviewedAtEpochMillis = now,
                    updatedAtEpochMillis = now
                )
                workspaceDao.updateSubmission(updated.toEntity())
                workspaceDao.insertAuditLog(
                    ApprovalAuditLogEntity(
                        submissionId = submissionId,
                        organizationId = existing.organizationId,
                        action = "APPROVED",
                        fromStatus = MedicineSubmissionStatus.PENDING_APPROVAL.name,
                        toStatus = MedicineSubmissionStatus.APPROVED.name,
                        actorUserId = reviewer.id,
                        actorName = reviewer.fullName,
                        notes = updated.reviewNotes ?: "Approved",
                        timestampEpochMillis = now
                    )
                )
                updated
            }

            // Notify submitter
            notificationDao.insertNotification(
                NotificationEntity(
                    recipientUserId = existing.submittedByUserId,
                    type = NotificationType.MEDICINE_APPROVED.name,
                    title = "Medicine Approved & Published",
                    message = "${existing.medicineName} (${existing.brandName}) was approved by ${reviewer.fullName} and is now discoverable in the catalogue.",
                    referenceId = (approvedResult.approvedProductId ?: submissionId).toString(),
                    createdAtEpochMillis = now
                )
            )
            approvedResult
        }
    }

    override suspend fun rejectMedicineSubmission(
        submissionId: Long,
        rejectionReason: String
    ): Result<MedicineSubmission> {
        return runCatching {
            val reviewer = requireAuthenticatedUser()
            val existing = workspaceDao.getSubmissionById(submissionId)?.toDomain()
                ?: throw NoSuchElementException("Medicine submission #$submissionId not found.")

            AccessControlPolicy.canRejectSubmission(
                reviewer = reviewer,
                submission = existing,
                reason = rejectionReason
            ).getOrThrow()

            if (isProductionHttpMode()) {
                val remote = remoteDataSource
                    ?: throw IllegalStateException("Remote workspace API is not initialized.")
                val rejectScope = "med-reject-$submissionId"
                when (
                    val res = remote.rejectMedicineSubmission(
                        submissionId = submissionId,
                        reason = rejectionReason.trim(),
                        idempotencyKey = obtainIdempotencyKey(rejectScope)
                    )
                ) {
                    is NetworkResult.Success -> {
                        releaseIdempotencyKey(rejectScope)
                        val saved = res.data.toEntity()
                        workspaceDao.insertSubmission(saved)
                        runCatching { syncApprovalAuditLogs(submissionId) }
                        return@runCatching saved.toDomain()
                    }
                    else -> throw res.toException()
                }
            }

            val now = System.currentTimeMillis()
            val updated = existing.copy(
                approvalStatus = MedicineSubmissionStatus.REJECTED,
                catalogueStatus = CataloguePublicationStatus.INACTIVE,
                reviewedByUserId = reviewer.id,
                reviewedByName = reviewer.fullName,
                rejectionReason = rejectionReason.trim(),
                reviewedAtEpochMillis = now,
                updatedAtEpochMillis = now
            )
            db.withTransaction {
                workspaceDao.updateSubmission(updated.toEntity())
                workspaceDao.insertAuditLog(
                    ApprovalAuditLogEntity(
                        submissionId = submissionId,
                        organizationId = existing.organizationId,
                        action = "REJECTED",
                        fromStatus = MedicineSubmissionStatus.PENDING_APPROVAL.name,
                        toStatus = MedicineSubmissionStatus.REJECTED.name,
                        actorUserId = reviewer.id,
                        actorName = reviewer.fullName,
                        notes = rejectionReason.trim(),
                        timestampEpochMillis = now
                    )
                )
            }
            notificationDao.insertNotification(
                NotificationEntity(
                    recipientUserId = existing.submittedByUserId,
                    type = NotificationType.MEDICINE_REJECTED.name,
                    title = "Medicine Submission Rejected",
                    message = "${existing.medicineName} was rejected by ${reviewer.fullName}: ${rejectionReason.trim()}",
                    referenceId = submissionId.toString(),
                    createdAtEpochMillis = now
                )
            )
            updated
        }
    }

    // =========================================================================
    // Company Catalogue & Master Data Management
    // =========================================================================

    override fun getCompanyCatalogueProductsFlow(): Flow<List<ProductEntity>> {
        return pharmaRepository.getApiUsersMeFlow().flatMapLatest { user ->
            if (user == null || user.organizationType == OrganizationType.STOCKIST) {
                flowOf(emptyList())
            } else {
                productDao.getAllProductsFlow().map { list ->
                    if (user.role == UserRole.ADMIN) list
                    else list.filter { it.organizationId == user.organizationId }
                }
            }
        }
    }

    override suspend fun updateCompanyCatalogueProduct(product: ProductEntity): Result<Unit> {
        return runCatching {
            val actor = requireAuthenticatedUser()
            AccessControlPolicy.canEditApprovedCatalogueProduct(actor, product.organizationId).getOrThrow()
            pharmaRepository.putApiProducts(product).getOrThrow()
        }
    }

    override suspend fun updateCataloguePublicationStatus(
        productId: Long,
        newStatus: CataloguePublicationStatus
    ): Result<Unit> {
        return runCatching {
            val actor = requirePermission(PharmaPermission.MEDICINE_CATALOGUE_MANAGE)
            val existing = productDao.getProductById(productId)
                ?: throw NoSuchElementException("Catalogue medicine #$productId not found.")
            if (!AccessControlPolicy.canOperateOnOrganization(actor, existing.organizationId)) {
                throw SecurityException("Cross-organization catalogue status change denied.")
            }
            if (isProductionHttpMode()) {
                val remote = remoteDataSource
                    ?: throw IllegalStateException("Remote workspace API is not initialized.")
                when (val res = remote.updateCatalogueStatus(productId, newStatus.name)) {
                    is NetworkResult.Success -> {
                        productDao.updateProduct(
                            existing.copy(
                                catalogueStatus = newStatus.name,
                                isActive = newStatus == CataloguePublicationStatus.ACTIVE
                            )
                        )
                        return@runCatching
                    }
                    else -> throw res.toException()
                }
            }
            productDao.updateProduct(
                existing.copy(
                    catalogueStatus = newStatus.name,
                    isActive = newStatus == CataloguePublicationStatus.ACTIVE
                )
            )
        }
    }

    override fun getMasterDataFlow(): Flow<List<MasterDataItem>> {
        return pharmaRepository.getApiUsersMeFlow().flatMapLatest { user ->
            if (user == null || user.organizationType == OrganizationType.STOCKIST) {
                flowOf(emptyList())
            } else {
                workspaceDao.getMasterDataForOrganizationFlow(user.organizationId).map { list ->
                    list.map { it.toDomain() }
                }
            }
        }
    }

    override suspend fun addMasterDataItem(
        type: MasterDataType,
        name: String,
        code: String,
        description: String,
        isOrganizationScoped: Boolean
    ): Result<MasterDataItem> {
        return runCatching {
            val requiredPerm = when (type) {
                MasterDataType.SALT_COMPOSITION -> PharmaPermission.COMPOSITION_MANAGE
                MasterDataType.BRAND, MasterDataType.GENERIC_MEDICINE -> PharmaPermission.BRAND_MANAGE
                MasterDataType.DOSAGE_FORM,
                MasterDataType.STRENGTH,
                MasterDataType.PACKAGING_TYPE,
                MasterDataType.PRODUCT_CLASSIFICATION -> PharmaPermission.DOSAGE_FORM_MANAGE
            }
            val actor = requirePermission(requiredPerm)
            val trimmedName = name.trim()
            if (trimmedName.length < 2) {
                throw IllegalArgumentException("Master data entry name must be at least 2 characters.")
            }
            val scopeOrgId = if (isOrganizationScoped || type == MasterDataType.BRAND) actor.organizationId else 0L
            val existingDup = workspaceDao.findDuplicateMasterData(
                type = type.name,
                normalizedName = trimmedName.lowercase(),
                orgId = actor.organizationId
            )
            if (existingDup != null) {
                throw IllegalStateException("Duplicate entry: '${existingDup.name}' already exists under ${type.displayName}.")
            }

            if (isProductionHttpMode()) {
                val remote = remoteDataSource
                    ?: throw IllegalStateException("Remote workspace API is not initialized.")
                when (
                    val res = remote.createMasterData(
                        MasterDataDto(
                            organizationId = scopeOrgId.takeIf { it > 0L },
                            type = type.name,
                            name = trimmedName,
                            code = code.trim(),
                            description = description.trim(),
                            isOrganizationScoped = scopeOrgId > 0L
                        )
                    )
                ) {
                    is NetworkResult.Success -> {
                        val entity = res.data.toEntity()
                        workspaceDao.insertMasterDataIgnoreDuplicate(entity)
                        return@runCatching entity.toDomain()
                    }
                    else -> throw res.toException()
                }
            }

            val entity = MasterDataEntity(
                organizationId = scopeOrgId,
                type = type.name,
                name = trimmedName,
                normalizedName = trimmedName.lowercase(),
                code = code.trim().uppercase(),
                description = description.trim(),
                isOrganizationScoped = scopeOrgId > 0L,
                isActive = true
            )
            val id = workspaceDao.insertMasterData(entity)
            entity.copy(id = id).toDomain()
        }
    }

    // =========================================================================
    // Manufacturer Offer Banners, Campaigns & Medicine Announcements
    // =========================================================================

    override fun getManufacturerBannersFlow(): Flow<List<OfferBanner>> {
        return pharmaRepository.getApiUsersMeFlow().flatMapLatest { user ->
            if (user == null || !AccessControlPolicy.canAccessWorkspace(user, WorkspaceType.MANUFACTURER)) {
                flowOf(emptyList())
            } else {
                workspaceDao.getBannersByOrganizationFlow(user.organizationId).map { list ->
                    list.map { it.toDomain() }
                }
            }
        }
    }

    override suspend fun saveOfferBanner(banner: OfferBanner): Result<OfferBanner> {
        return runCatching {
            val requiredPerm = if (banner.id > 0L) PharmaPermission.BANNER_UPDATE else PharmaPermission.BANNER_CREATE
            val actor = requirePermission(requiredPerm)
            if (actor.organizationType != OrganizationType.PHARMACEUTICAL_ORGANIZATION && actor.role != UserRole.ADMIN) {
                throw SecurityException("Only authorized Manufacturer accounts can manage offer banners.")
            }
            if (banner.title.isBlank()) throw IllegalArgumentException("Banner title is required.")
            if (banner.discountOrOfferSummary.isBlank()) {
                throw IllegalArgumentException("Offer / discount summary is required.")
            }
            if (banner.endDateEpochMillis <= banner.startDateEpochMillis) {
                throw IllegalArgumentException("Banner end date must be after start date.")
            }
            if (banner.id > 0L) {
                val existing = workspaceDao.getBannerById(banner.id)
                    ?: throw NoSuchElementException("Offer banner not found.")
                if (!AccessControlPolicy.canOperateOnOrganization(actor, existing.organizationId)) {
                    throw SecurityException("Cross-organization banner modification is forbidden.")
                }
            }
            if (banner.linkedProductId != null) {
                val linked = productDao.getProductById(banner.linkedProductId)
                    ?: throw IllegalArgumentException("Linked product does not exist.")
                if (linked.approvalStatus != MedicineSubmissionStatus.APPROVED.name) {
                    throw IllegalStateException("Offer banners can only link to APPROVED catalogue medicines.")
                }
                if (!AccessControlPolicy.canOperateOnOrganization(actor, linked.organizationId) && linked.organizationId > 0L) {
                    throw SecurityException("Cannot link offer banner to another organization's product.")
                }
            }

            val isProd = isProductionHttpMode()
            if (isProd && !MediaUploadManager.isPersistableProductionMediaUrl(banner.imageUrl)) {
                throw IllegalArgumentException("Upload banner creative to the server before publishing in production mode.")
            }
            val now = System.currentTimeMillis()
            val prepared = banner.copy(
                organizationId = actor.organizationId,
                organizationName = actor.organizationName,
                createdByUserId = actor.id,
                ctaDestinationRoute = banner.ctaDestinationRoute.ifBlank {
                    banner.linkedProductId?.let { "medicine_details/$it" } ?: "medicine_catalogue"
                },
                updatedAtEpochMillis = now
            )

            if (isProd) {
                val remote = remoteDataSource
                    ?: throw IllegalStateException("Remote workspace API is not initialized.")
                val opScope = "banner-save-${actor.organizationId}-${prepared.id}-${prepared.title.trim().lowercase()}"
                when (
                    val res = remote.saveOfferBanner(
                        dto = prepared.toDto(),
                        idempotencyKey = obtainIdempotencyKey(opScope)
                    )
                ) {
                    is NetworkResult.Success -> {
                        releaseIdempotencyKey(opScope)
                        val saved = res.data.toEntity()
                        workspaceDao.insertBanner(saved)
                        return@runCatching saved.toDomain()
                    }
                    else -> throw res.toException()
                }
            }

            val id = workspaceDao.insertBanner(prepared.toEntity())
            val saved = prepared.copy(id = id)
            if (saved.isEligibleForStockist(now)) {
                notifyStockists(
                    type = NotificationType.BANNER_PUBLISHED,
                    title = "New Trade Offer: ${saved.title}",
                    message = "${saved.organizationName}: ${saved.discountOrOfferSummary}",
                    referenceId = id
                )
            }
            saved
        }
    }

    override suspend fun updateOfferBannerStatus(
        bannerId: Long,
        newStatus: BannerStatus
    ): Result<OfferBanner> {
        return runCatching {
            val actor = requirePermission(PharmaPermission.BANNER_UPDATE)
            val existing = workspaceDao.getBannerById(bannerId)?.toDomain()
                ?: throw NoSuchElementException("Banner #$bannerId not found.")
            if (!AccessControlPolicy.canOperateOnOrganization(actor, existing.organizationId)) {
                throw SecurityException("Cross-organization banner status update denied.")
            }
            if (isProductionHttpMode()) {
                val remote = remoteDataSource
                    ?: throw IllegalStateException("Remote workspace API is not initialized.")
                when (val res = remote.updateOfferBannerStatus(bannerId, newStatus.name)) {
                    is NetworkResult.Success -> {
                        val entity = res.data.toEntity()
                        workspaceDao.insertBanner(entity)
                        return@runCatching entity.toDomain()
                    }
                    else -> throw res.toException()
                }
            }
            val now = System.currentTimeMillis()
            val updated = existing.copy(status = newStatus, updatedAtEpochMillis = now)
            workspaceDao.updateBanner(updated.toEntity())
            if (newStatus == BannerStatus.ACTIVE && updated.isEligibleForStockist(now)) {
                notifyStockists(
                    type = NotificationType.BANNER_PUBLISHED,
                    title = "Offer Now Live: ${updated.title}",
                    message = "${updated.organizationName}: ${updated.discountOrOfferSummary}",
                    referenceId = bannerId
                )
            }
            updated
        }
    }

    override suspend fun deleteOfferBanner(bannerId: Long): Result<Unit> {
        return runCatching {
            val actor = requirePermission(PharmaPermission.BANNER_DELETE)
            val existing = workspaceDao.getBannerById(bannerId)
                ?: throw NoSuchElementException("Banner #$bannerId not found.")
            if (!AccessControlPolicy.canOperateOnOrganization(actor, existing.organizationId)) {
                throw SecurityException("Cross-organization banner deletion is forbidden.")
            }
            if (isProductionHttpMode()) {
                val remote = remoteDataSource
                    ?: throw IllegalStateException("Remote workspace API is not initialized.")
                when (val res = remote.deleteOfferBanner(bannerId)) {
                    is NetworkResult.Success -> {
                        workspaceDao.deleteBanner(bannerId, existing.organizationId)
                        return@runCatching
                    }
                    else -> throw res.toException()
                }
            }
            workspaceDao.deleteBanner(bannerId, existing.organizationId)
        }
    }

    override fun getManufacturerCampaignsFlow(): Flow<List<PromotionalCampaign>> {
        return pharmaRepository.getApiUsersMeFlow().flatMapLatest { user ->
            if (user == null || !AccessControlPolicy.canAccessWorkspace(user, WorkspaceType.MANUFACTURER)) {
                flowOf(emptyList())
            } else {
                workspaceDao.getCampaignsByOrganizationFlow(user.organizationId).map { list ->
                    list.map { it.toDomain() }
                }
            }
        }
    }

    override suspend fun savePromotionalCampaign(campaign: PromotionalCampaign): Result<PromotionalCampaign> {
        return runCatching {
            val actor = requirePermission(PharmaPermission.CAMPAIGN_MANAGE)
            if (campaign.name.isBlank()) throw IllegalArgumentException("Campaign name is required.")
            if (campaign.schemeSummary.isBlank()) throw IllegalArgumentException("Scheme summary is required.")
            if (campaign.endDateEpochMillis <= campaign.startDateEpochMillis) {
                throw IllegalArgumentException("Campaign end date must be after start date.")
            }
            val prepared = campaign.copy(
                organizationId = actor.organizationId,
                organizationName = actor.organizationName,
                createdByUserId = actor.id
            )
            if (isProductionHttpMode()) {
                val remote = remoteDataSource
                    ?: throw IllegalStateException("Remote workspace API is not initialized.")
                when (val res = remote.savePromotionalCampaign(prepared.toDto())) {
                    is NetworkResult.Success -> {
                        val entity = res.data.toEntity()
                        workspaceDao.insertCampaign(entity)
                        return@runCatching entity.toDomain()
                    }
                    else -> throw res.toException()
                }
            }
            val id = workspaceDao.insertCampaign(prepared.toEntity())
            prepared.copy(id = id)
        }
    }

    override suspend fun updateCampaignStatus(
        campaignId: Long,
        newStatus: CampaignStatus
    ): Result<PromotionalCampaign> {
        return runCatching {
            val actor = requirePermission(PharmaPermission.CAMPAIGN_MANAGE)
            val existing = workspaceDao.getCampaignById(campaignId)?.toDomain()
                ?: throw NoSuchElementException("Campaign #$campaignId not found.")
            if (!AccessControlPolicy.canOperateOnOrganization(actor, existing.organizationId)) {
                throw SecurityException("Cross-organization campaign update denied.")
            }
            val updated = existing.copy(status = newStatus)
            if (isProductionHttpMode()) {
                val remote = remoteDataSource
                    ?: throw IllegalStateException("Remote workspace API is not initialized.")
                when (val res = remote.savePromotionalCampaign(updated.toDto())) {
                    is NetworkResult.Success -> {
                        val entity = res.data.toEntity()
                        workspaceDao.insertCampaign(entity)
                        return@runCatching entity.toDomain()
                    }
                    else -> throw res.toException()
                }
            }
            workspaceDao.updateCampaign(updated.toEntity())
            updated
        }
    }

    override fun getManufacturerAnnouncementsFlow(): Flow<List<MedicineAnnouncement>> {
        return pharmaRepository.getApiUsersMeFlow().flatMapLatest { user ->
            if (user == null || !AccessControlPolicy.canAccessWorkspace(user, WorkspaceType.MANUFACTURER)) {
                flowOf(emptyList())
            } else {
                workspaceDao.getAnnouncementsByOrganizationFlow(user.organizationId).map { list ->
                    list.map { it.toDomain() }
                }
            }
        }
    }

    override fun getApprovedProductsForOrganizationFlow(): Flow<List<ProductEntity>> {
        return pharmaRepository.getApiUsersMeFlow().flatMapLatest { user ->
            if (user == null || user.organizationType == OrganizationType.STOCKIST) {
                flowOf(emptyList())
            } else {
                productDao.getAllProductsFlow().map { list ->
                    list.filter { p ->
                        (p.organizationId == user.organizationId || user.role == UserRole.ADMIN) &&
                            p.approvalStatus == MedicineSubmissionStatus.APPROVED.name &&
                            p.catalogueStatus == CataloguePublicationStatus.ACTIVE.name &&
                            p.isActive
                    }
                }
            }
        }
    }

    override suspend fun saveMedicineAnnouncement(
        announcement: MedicineAnnouncement
    ): Result<MedicineAnnouncement> {
        return runCatching {
            val actor = requirePermission(PharmaPermission.MEDICINE_ANNOUNCEMENT_CREATE)
            if (announcement.title.isBlank() || announcement.body.isBlank()) {
                throw IllegalArgumentException("Announcement title and details are required.")
            }
            val linked = productDao.getProductById(announcement.linkedProductId)
                ?: throw IllegalArgumentException("Please select a valid approved medicine.")
            if (linked.approvalStatus != MedicineSubmissionStatus.APPROVED.name) {
                throw IllegalStateException("Medicine announcements can only be created for APPROVED medicines.")
            }
            if (!AccessControlPolicy.canOperateOnOrganization(actor, linked.organizationId) && linked.organizationId > 0L) {
                throw SecurityException("Cannot announce medicines belonging to another organization.")
            }
            val isProd = isProductionHttpMode()
            if (isProd && !MediaUploadManager.isPersistableProductionMediaUrl(announcement.imageUrl)) {
                throw IllegalArgumentException("Upload announcement artwork to the server before publishing in production mode.")
            }
            val now = System.currentTimeMillis()
            val prepared = announcement.copy(
                organizationId = actor.organizationId,
                organizationName = actor.organizationName,
                linkedProductName = linked.name,
                createdByUserId = actor.id,
                publishedAtEpochMillis = if (announcement.status == AnnouncementStatus.PUBLISHED) {
                    announcement.publishedAtEpochMillis ?: now
                } else null
            )
            if (isProd) {
                val remote = remoteDataSource
                    ?: throw IllegalStateException("Remote workspace API is not initialized.")
                val opScope = "announcement-save-${actor.organizationId}-${prepared.id}-${prepared.title.trim().lowercase()}"
                when (
                    val res = remote.saveMedicineAnnouncement(
                        dto = prepared.toDto(),
                        idempotencyKey = obtainIdempotencyKey(opScope)
                    )
                ) {
                    is NetworkResult.Success -> {
                        releaseIdempotencyKey(opScope)
                        val entity = res.data.toEntity()
                        workspaceDao.insertAnnouncement(entity)
                        return@runCatching entity.toDomain()
                    }
                    else -> throw res.toException()
                }
            }
            val id = workspaceDao.insertAnnouncement(prepared.toEntity())
            val saved = prepared.copy(id = id)
            if (saved.status == AnnouncementStatus.PUBLISHED) {
                notifyStockists(
                    type = NotificationType.MEDICINE_ANNOUNCEMENT,
                    title = "${saved.category.displayName}: ${saved.linkedProductName}",
                    message = "${saved.organizationName} — ${saved.title}",
                    referenceId = saved.linkedProductId
                )
            }
            saved
        }
    }

    override suspend fun updateAnnouncementStatus(
        announcementId: Long,
        newStatus: AnnouncementStatus
    ): Result<MedicineAnnouncement> {
        return runCatching {
            val actor = requirePermission(PharmaPermission.MEDICINE_ANNOUNCEMENT_CREATE)
            val existing = workspaceDao.getAnnouncementById(announcementId)?.toDomain()
                ?: throw NoSuchElementException("Announcement #$announcementId not found.")
            if (!AccessControlPolicy.canOperateOnOrganization(actor, existing.organizationId)) {
                throw SecurityException("Cross-organization announcement update denied.")
            }
            val now = System.currentTimeMillis()
            val updated = existing.copy(
                status = newStatus,
                publishedAtEpochMillis = if (newStatus == AnnouncementStatus.PUBLISHED) now else existing.publishedAtEpochMillis
            )
            if (isProductionHttpMode()) {
                val remote = remoteDataSource
                    ?: throw IllegalStateException("Remote workspace API is not initialized.")
                when (val res = remote.saveMedicineAnnouncement(updated.toDto())) {
                    is NetworkResult.Success -> {
                        val entity = res.data.toEntity()
                        workspaceDao.insertAnnouncement(entity)
                        return@runCatching entity.toDomain()
                    }
                    else -> throw res.toException()
                }
            }
            workspaceDao.updateAnnouncement(updated.toEntity())
            if (newStatus == AnnouncementStatus.PUBLISHED) {
                notifyStockists(
                    type = NotificationType.MEDICINE_ANNOUNCEMENT,
                    title = "${updated.category.displayName}: ${updated.linkedProductName}",
                    message = "${updated.organizationName} — ${updated.title}",
                    referenceId = updated.linkedProductId
                )
            }
            updated
        }
    }

    // =========================================================================
    // Stockist Dynamic Promotional Feed & Strictly Isolated Purchased Inventory
    // =========================================================================

    override fun getStockistActiveBannersFlow(): Flow<List<OfferBanner>> {
        return combine(
            workspaceDao.getCandidatePublicBannersFlow(),
            bannerClockMillisFlow
        ) { entities, now ->
            entities.map { it.toDomain() }
                .filter { it.isEligibleForStockist(now) }
                .sortedWith(compareBy<OfferBanner> { it.priorityOrder }.thenByDescending { it.updatedAtEpochMillis })
        }
    }

    override fun getStockistPublishedAnnouncementsFlow(): Flow<List<MedicineAnnouncement>> {
        return workspaceDao.getPublishedAnnouncementsFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getStockistPurchasedInventoryFlow(): Flow<List<StockistPurchasedInventoryItem>> {
        return pharmaRepository.getApiUsersMeFlow().flatMapLatest { user ->
            if (user == null || !AccessControlPolicy.canAccessStockistPurchasedInventory(user)) {
                flowOf(emptyList())
            } else {
                workspaceDao.getStockistPurchasedInventoryFlow(user.organizationId).map { list ->
                    list.map { it.toDomain() }
                }
            }
        }
    }

    override suspend fun upsertStockistPurchasedInventory(
        item: StockistPurchasedInventoryItem
    ): Result<StockistPurchasedInventoryItem> {
        return runCatching {
            val actor = requirePermission(PharmaPermission.STOCKIST_INVENTORY_MANAGE)
            if (!AccessControlPolicy.canAccessStockistPurchasedInventory(actor)) {
                throw SecurityException("Stockist Purchased Inventory is restricted to Stockist accounts.")
            }
            if (item.productName.isBlank()) throw IllegalArgumentException("Medicine name is required.")
            if (item.batchNumber.isBlank()) throw IllegalArgumentException("Batch number is required.")
            if (item.remainingQuantity < 0 || item.purchasedQuantity < 0) {
                throw IllegalArgumentException("Inventory quantities cannot be negative.")
            }
            val prepared = item.copy(
                stockistOrganizationId = actor.organizationId,
                stockistUserId = actor.id,
                updatedAtEpochMillis = System.currentTimeMillis()
            )
            if (isProductionHttpMode()) {
                val remote = remoteDataSource
                    ?: throw IllegalStateException("Remote workspace API is not initialized.")
                when (val res = remote.upsertStockistPurchasedInventory(prepared.toDto())) {
                    is NetworkResult.Success -> {
                        val entity = res.data.toEntity()
                        workspaceDao.upsertStockistInventoryItem(entity)
                        return@runCatching entity.toDomain()
                    }
                    else -> throw res.toException()
                }
            }
            val id = workspaceDao.upsertStockistInventoryItem(prepared.toEntity())
            prepared.copy(id = id)
        }
    }

    override suspend fun adjustStockistPurchasedQuantity(
        itemId: Long,
        newRemainingQuantity: Int
    ): Result<StockistPurchasedInventoryItem> {
        return runCatching {
            val actor = requirePermission(PharmaPermission.STOCKIST_INVENTORY_MANAGE)
            val existing = workspaceDao.getStockistInventoryItemById(itemId, actor.organizationId)?.toDomain()
                ?: throw NoSuchElementException("Stockist inventory item #$itemId not found.")
            if (newRemainingQuantity < 0) {
                throw IllegalArgumentException("Remaining stock cannot be negative.")
            }
            val updated = existing.copy(
                remainingQuantity = newRemainingQuantity,
                updatedAtEpochMillis = System.currentTimeMillis()
            )
            upsertStockistPurchasedInventory(updated).getOrThrow()
        }
    }

    override suspend fun deleteStockistPurchasedInventory(itemId: Long): Result<Unit> {
        return runCatching {
            val actor = requirePermission(PharmaPermission.STOCKIST_INVENTORY_MANAGE)
            if (isProductionHttpMode()) {
                val remote = remoteDataSource
                    ?: throw IllegalStateException("Remote workspace API is not initialized.")
                when (val res = remote.deleteStockistPurchasedInventory(itemId)) {
                    is NetworkResult.Success -> {
                        workspaceDao.deleteStockistInventoryItem(itemId, actor.organizationId)
                        return@runCatching
                    }
                    else -> throw res.toException()
                }
            }
            workspaceDao.deleteStockistInventoryItem(itemId, actor.organizationId)
        }
    }

    override suspend fun clearSensitiveLocalSessionCaches(previousUser: AuthUser?) {
        // Cancel any in-flight workspace sync jobs so stale responses cannot repopulate local tables
        cancelActiveSync()
        operationIdempotencyKeys.clear()
        _workspaceSyncStateFlow.value = WorkspaceSyncState()
        dataStoreManager.setActiveWorkspace(null)
        // In PRODUCTION_BACKEND_HTTP mode, clear ALL private local Room caches on logout/account switch.
        // This clears ONLY device-local cached data and never deletes records on the Spring Boot backend.
        if (isProductionHttpMode()) {
            db.withTransaction {
                workspaceDao.clearAllPrivateSessionCaches()
            }
        }
    }

    // =========================================================================
    // Role-Specific Notification Helpers
    // =========================================================================

    private suspend fun notifyCompanyApprovers(
        organizationId: Long,
        excludeUserId: Long,
        title: String,
        message: String,
        referenceId: Long
    ) {
        val members = workspaceDao.getOrganizationMembers(organizationId)
        val approvers = members.filter { member ->
            member.userId != excludeUserId &&
                PharmaPermission.parseCsv(member.permissionsCsv).contains(PharmaPermission.MEDICINE_APPROVE)
        }
        val now = System.currentTimeMillis()
        approvers.forEach { approver ->
            notificationDao.insertNotification(
                NotificationEntity(
                    recipientUserId = approver.userId,
                    type = NotificationType.MEDICINE_SUBMITTED.name,
                    title = title,
                    message = message,
                    referenceId = referenceId.toString(),
                    createdAtEpochMillis = now
                )
            )
        }
    }

    private suspend fun notifyStockists(
        type: NotificationType,
        title: String,
        message: String,
        referenceId: Long
    ) {
        val stockists = userDao.getAllUsers().filter { it.role == UserRole.STOCKIST.name }
        val now = System.currentTimeMillis()
        stockists.forEach { stockist ->
            notificationDao.insertNotification(
                NotificationEntity(
                    recipientUserId = stockist.id,
                    type = type.name,
                    title = title,
                    message = message,
                    referenceId = referenceId.toString(),
                    createdAtEpochMillis = now
                )
            )
        }
    }

    // =========================================================================
    // Remote Read Synchronization (All 12 Datasets — Strictly Organization Scoped)
    // =========================================================================

    override suspend fun syncAuthContext(): Result<AuthUser> {
        return runCatching {
            if (!isProductionHttpMode()) {
                return@runCatching requireAuthenticatedUser()
            }
            val remote = remoteDataSource
                ?: throw IllegalStateException("Remote workspace API is not initialized.")
            when (val res = remote.fetchAuthContext()) {
                is NetworkResult.Success -> {
                    val ctx = res.data
                    val orgDto = ctx.organization
                    val enrichedUserDto = ctx.user.copy(
                        organizationId = if (orgDto.id > 0L) orgDto.id else ctx.user.organizationId,
                        organizationType = orgDto.type.ifBlank { ctx.user.organizationType ?: "" },
                        organizationName = orgDto.name.ifBlank { ctx.user.organizationName ?: "" },
                        roles = ctx.roles.ifEmpty { ctx.user.roles },
                        permissions = ctx.permissions
                    )
                    val userEntity = enrichedUserDto.toUserEntityCache(isProductionMode = true)
                    val savedId = db.withTransaction {
                        if (orgDto.id > 0L) {
                            workspaceDao.upsertOrganization(
                                OrganizationEntity(
                                    id = orgDto.id,
                                    name = orgDto.name,
                                    type = orgDto.type,
                                    gstin = orgDto.gstin,
                                    drugLicenseNumber = orgDto.drugLicenseNumber,
                                    address = orgDto.address,
                                    isActive = orgDto.isActive,
                                    createdAtEpochMillis = orgDto.createdAtEpochMillis
                                )
                            )
                        }
                        val existing = userDao.getUserById(userEntity.id)
                            ?: userDao.getUserByEmail(userEntity.email.lowercase())
                        if (existing != null) {
                            userDao.updateUser(
                                existing.copy(
                                    fullName = userEntity.fullName,
                                    businessName = userEntity.businessName,
                                    email = userEntity.email,
                                    phone = userEntity.phone,
                                    gstin = userEntity.gstin,
                                    address = userEntity.address,
                                    role = userEntity.role,
                                    status = userEntity.status,
                                    emailVerified = userEntity.emailVerified,
                                    phoneVerified = userEntity.phoneVerified,
                                    profileBio = userEntity.profileBio,
                                    organizationId = userEntity.organizationId,
                                    organizationType = userEntity.organizationType,
                                    organizationName = userEntity.organizationName,
                                    assignedRolesCsv = userEntity.assignedRolesCsv,
                                    permissionsCsv = userEntity.permissionsCsv
                                )
                            )
                            existing.id
                        } else {
                            userDao.insertUser(userEntity)
                        }
                    }
                    dataStoreManager.setLoggedInUserId(savedId)
                    enrichedUserDto.toAuthUser(isProductionMode = true).copy(id = savedId)
                }
                else -> throw res.toException()
            }
        }
    }

    override suspend fun syncOrganizationMembers(): Result<Unit> {
        return runCatching {
            if (!isProductionHttpMode()) return@runCatching
            val actor = requireAuthenticatedUser()
            if (!actor.hasValidOrganizationContext()) return@runCatching
            val remote = remoteDataSource
                ?: throw IllegalStateException("Remote workspace API is not initialized.")
            when (val res = remote.fetchOrganizationMembers(actor.organizationId)) {
                is NetworkResult.Success -> {
                    val scoped = res.data
                        .filter { it.organizationId == actor.organizationId || actor.role == UserRole.ADMIN }
                        .map { it.toEntity(isProductionMode = true) }
                    db.withTransaction {
                        workspaceDao.deleteMembersByOrganization(actor.organizationId)
                        scoped.forEach { workspaceDao.upsertMember(it) }
                    }
                }
                else -> throw res.toException()
            }
        }
    }

    override suspend fun syncMedicineSubmissions(): Result<Unit> {
        return runCatching {
            if (!isProductionHttpMode()) return@runCatching
            val actor = requireAuthenticatedUser()
            if (actor.organizationType == OrganizationType.STOCKIST || !actor.hasValidOrganizationContext()) {
                return@runCatching
            }
            val remote = remoteDataSource
                ?: throw IllegalStateException("Remote workspace API is not initialized.")
            when (val res = remote.fetchMedicineSubmissions()) {
                is NetworkResult.Success -> {
                    val scoped = res.data
                        .filter { it.organizationId == actor.organizationId || actor.role == UserRole.ADMIN }
                        .map { it.toEntity() }
                    db.withTransaction {
                        workspaceDao.deleteSubmissionsByOrganization(actor.organizationId)
                        scoped.forEach { workspaceDao.insertSubmission(it) }
                    }
                }
                else -> throw res.toException()
            }
        }
    }

    override suspend fun syncApprovalAuditLogs(submissionId: Long?): Result<Unit> {
        return runCatching {
            if (!isProductionHttpMode()) return@runCatching
            val actor = requireAuthenticatedUser()
            if (actor.organizationType == OrganizationType.STOCKIST || !actor.hasValidOrganizationContext()) {
                return@runCatching
            }
            val remote = remoteDataSource
                ?: throw IllegalStateException("Remote workspace API is not initialized.")
            val targetIds = if (submissionId != null) {
                listOf(submissionId)
            } else {
                workspaceDao.getSubmissionsByOrganization(actor.organizationId).map { it.id }
            }
            for (subId in targetIds) {
                when (val res = remote.fetchSubmissionAuditLogs(subId)) {
                    is NetworkResult.Success -> {
                        val entries = res.data
                            .filter { it.organizationId == actor.organizationId || actor.role == UserRole.ADMIN }
                            .map { it.toEntity() }
                        db.withTransaction {
                            workspaceDao.deleteAuditLogsBySubmission(subId)
                            entries.forEach { workspaceDao.insertAuditLog(it) }
                        }
                    }
                    else -> if (submissionId != null) throw res.toException()
                }
            }
        }
    }

    override suspend fun syncCompanyCatalogue(): Result<Unit> {
        return try {
            if (!isProductionHttpMode()) return Result.success(Unit)
            val actor = requireAuthenticatedUser()
            if (actor.organizationType == OrganizationType.STOCKIST || !actor.hasValidOrganizationContext()) {
                return Result.success(Unit)
            }
            val remote = remoteDataSource
                ?: return Result.failure(IllegalStateException("Remote workspace API is not initialized."))
            when (val res = remote.fetchCompanyCatalogue()) {
                is NetworkResult.Success -> {
                    val authoritativeEntities = mutableListOf<ProductEntity>()
                    var rejectedCount = 0
                    for (dto in res.data) {
                        val dtoOrgId = dto.organizationId?.takeIf { it > 0L }
                        // SECURITY: Never assign actor.organizationId when backend omits organizationId
                        if (dtoOrgId == null) {
                            rejectedCount++
                            continue
                        }
                        // Enforce strict organization ownership isolation
                        if (actor.role != UserRole.ADMIN && dtoOrgId != actor.organizationId) {
                            rejectedCount++
                            continue
                        }
                        // SECURITY: Never default missing/unknown approvalStatus or catalogueStatus to APPROVED/ACTIVE
                        val parsedApproval = MedicineSubmissionStatus.fromString(dto.approvalStatus)
                        val parsedCatalogue = CataloguePublicationStatus.fromString(dto.catalogueStatus)
                        if (parsedApproval == MedicineSubmissionStatus.UNKNOWN ||
                            parsedCatalogue == CataloguePublicationStatus.UNKNOWN
                        ) {
                            rejectedCount++
                            continue
                        }
                        authoritativeEntities.add(dto.toEntity(isProductionMode = true))
                    }

                    if (res.data.isNotEmpty() && authoritativeEntities.isEmpty()) {
                        return Result.failure(
                            IllegalStateException(
                                "Catalogue sync rejected all ${res.data.size} record(s): missing authoritative organizationId, approvalStatus, or catalogueStatus."
                            )
                        )
                    }

                    db.withTransaction {
                        // Reconcile removed or outdated records for the actor's organization
                        if (actor.role != UserRole.ADMIN) {
                            val existingForOrg = productDao.getProductsByOrganization(actor.organizationId)
                            val authoritativeIds = authoritativeEntities.map { it.id }.toSet()
                            val removedIds = existingForOrg
                                .map { it.id }
                                .filter { it !in authoritativeIds }
                            if (removedIds.isNotEmpty()) {
                                productDao.deleteProductsByIds(removedIds)
                            }
                        }
                        authoritativeEntities.forEach { entity ->
                            productDao.insertProduct(entity)
                        }
                    }

                    if (rejectedCount > 0) {
                        Result.failure(
                            IllegalStateException(
                                "Catalogue synced ${authoritativeEntities.size} valid medicine(s); skipped $rejectedCount record(s) with unverified ownership or status."
                            )
                        )
                    } else {
                        Result.success(Unit)
                    }
                }
                else -> Result.failure(res.toException())
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    override suspend fun syncMasterData(): Result<Unit> {
        return runCatching {
            if (!isProductionHttpMode()) return@runCatching
            val actor = requireAuthenticatedUser()
            if (actor.organizationType == OrganizationType.STOCKIST || !actor.hasValidOrganizationContext()) {
                return@runCatching
            }
            val remote = remoteDataSource
                ?: throw IllegalStateException("Remote workspace API is not initialized.")
            when (val res = remote.fetchMasterData()) {
                is NetworkResult.Success -> {
                    val scoped = res.data
                        .filter { (it.organizationId ?: 0L) == 0L || it.organizationId == actor.organizationId || actor.role == UserRole.ADMIN }
                        .map { it.toEntity() }
                    db.withTransaction {
                        workspaceDao.deleteMasterDataForOrganizationScope(actor.organizationId)
                        scoped.forEach { workspaceDao.insertMasterDataIgnoreDuplicate(it) }
                    }
                }
                else -> throw res.toException()
            }
        }
    }

    override suspend fun syncManufacturerBanners(): Result<Unit> {
        return runCatching {
            if (!isProductionHttpMode()) return@runCatching
            val actor = requireAuthenticatedUser()
            if (!AccessControlPolicy.canAccessWorkspace(actor, WorkspaceType.MANUFACTURER)) return@runCatching
            val remote = remoteDataSource
                ?: throw IllegalStateException("Remote workspace API is not initialized.")
            when (val res = remote.fetchManufacturerBanners()) {
                is NetworkResult.Success -> {
                    val scoped = res.data
                        .filter { it.organizationId == actor.organizationId || actor.role == UserRole.ADMIN }
                        .map { it.toEntity() }
                    db.withTransaction {
                        workspaceDao.deleteBannersByOrganization(actor.organizationId)
                        scoped.forEach { workspaceDao.insertBanner(it) }
                    }
                }
                else -> throw res.toException()
            }
        }
    }

    override suspend fun syncPromotionalCampaigns(): Result<Unit> {
        return runCatching {
            if (!isProductionHttpMode()) return@runCatching
            val actor = requireAuthenticatedUser()
            if (!AccessControlPolicy.canAccessWorkspace(actor, WorkspaceType.MANUFACTURER)) return@runCatching
            val remote = remoteDataSource
                ?: throw IllegalStateException("Remote workspace API is not initialized.")
            when (val res = remote.fetchManufacturerCampaigns()) {
                is NetworkResult.Success -> {
                    val scoped = res.data
                        .filter { it.organizationId == actor.organizationId || actor.role == UserRole.ADMIN }
                        .map { it.toEntity() }
                    db.withTransaction {
                        workspaceDao.deleteCampaignsByOrganization(actor.organizationId)
                        scoped.forEach { workspaceDao.insertCampaign(it) }
                    }
                }
                else -> throw res.toException()
            }
        }
    }

    override suspend fun syncManufacturerAnnouncements(): Result<Unit> {
        return runCatching {
            if (!isProductionHttpMode()) return@runCatching
            val actor = requireAuthenticatedUser()
            if (!AccessControlPolicy.canAccessWorkspace(actor, WorkspaceType.MANUFACTURER)) return@runCatching
            val remote = remoteDataSource
                ?: throw IllegalStateException("Remote workspace API is not initialized.")
            when (val res = remote.fetchManufacturerAnnouncements()) {
                is NetworkResult.Success -> {
                    val scoped = res.data
                        .filter { it.organizationId == actor.organizationId || actor.role == UserRole.ADMIN }
                        .map { it.toEntity() }
                    db.withTransaction {
                        workspaceDao.deleteAnnouncementsByOrganization(actor.organizationId)
                        scoped.forEach { workspaceDao.insertAnnouncement(it) }
                    }
                }
                else -> throw res.toException()
            }
        }
    }

    override suspend fun syncStockistFeed(): Result<Unit> {
        return runCatching {
            if (!isProductionHttpMode()) return@runCatching
            val remote = remoteDataSource
                ?: throw IllegalStateException("Remote workspace API is not initialized.")
            val bannersRes = remote.fetchStockistActiveBanners()
            if (bannersRes is NetworkResult.Success) {
                val mapped = bannersRes.data.map { it.toEntity() }
                db.withTransaction {
                    workspaceDao.deletePublicCandidateBanners()
                    mapped.forEach { workspaceDao.insertBanner(it) }
                }
            }
            val announcementsRes = remote.fetchStockistPublishedAnnouncements()
            if (announcementsRes is NetworkResult.Success) {
                val mapped = announcementsRes.data.map { it.toEntity() }
                db.withTransaction {
                    workspaceDao.deletePublishedAnnouncements()
                    mapped.forEach { workspaceDao.insertAnnouncement(it) }
                }
            }
            if (bannersRes !is NetworkResult.Success && announcementsRes !is NetworkResult.Success) {
                throw bannersRes.toException()
            }
        }
    }

    override suspend fun syncStockistPurchasedInventory(): Result<Unit> {
        return runCatching {
            if (!isProductionHttpMode()) return@runCatching
            val actor = requireAuthenticatedUser()
            if (!AccessControlPolicy.canAccessStockistPurchasedInventory(actor)) return@runCatching
            val remote = remoteDataSource
                ?: throw IllegalStateException("Remote workspace API is not initialized.")
            when (val res = remote.fetchStockistPurchasedInventory()) {
                is NetworkResult.Success -> {
                    val scoped = res.data
                        .filter {
                            it.stockistOrganizationId == actor.organizationId &&
                                (it.stockistUserId == 0L || it.stockistUserId == actor.id || actor.role == UserRole.ADMIN)
                        }
                        .map { it.toEntity() }
                    db.withTransaction {
                        workspaceDao.clearStockistInventoryForOrg(actor.organizationId)
                        scoped.forEach { workspaceDao.upsertStockistInventoryItem(it) }
                    }
                }
                else -> throw res.toException()
            }
        }
    }

    override suspend fun syncActiveWorkspace(): Result<Unit> {
        if (!isProductionHttpMode()) {
            val now = System.currentTimeMillis()
            _workspaceSyncStateFlow.value = WorkspaceSyncState(
                status = SyncHealthStatus.SUCCESS,
                isStaleCache = false,
                lastSuccessfulSyncEpochMillis = now,
                lastAttemptEpochMillis = now,
                canRetry = false
            )
            return Result.success(Unit)
        }

        val currentJob = currentCoroutineContext()[Job]
        activeSyncJobRef.set(currentJob)
        val attemptStartedAt = System.currentTimeMillis()
        val previousSuccessTimestamp = _workspaceSyncStateFlow.value.lastSuccessfulSyncEpochMillis
        _workspaceSyncStateFlow.value = _workspaceSyncStateFlow.value.copy(
            status = SyncHealthStatus.SYNCING,
            lastAttemptEpochMillis = attemptStartedAt,
            errorMessage = null,
            canRetry = false
        )

        val stepResults = mutableListOf<SyncStepResult>()

        suspend fun runStep(
            name: String,
            required: Boolean,
            block: suspend () -> Result<Unit>
        ) {
            try {
                val res = block()
                if (res.isSuccess) {
                    stepResults.add(SyncStepResult(stepName = name, required = required, succeeded = true))
                } else {
                    val cause = res.exceptionOrNull()
                    if (cause is CancellationException) throw cause
                    stepResults.add(
                        SyncStepResult(
                            stepName = name,
                            required = required,
                            succeeded = false,
                            errorMessage = cause?.message ?: "Failed to sync $name."
                        )
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                stepResults.add(
                    SyncStepResult(
                        stepName = name,
                        required = required,
                        succeeded = false,
                        errorMessage = t.message ?: "Failed to sync $name."
                    )
                )
            }
        }

        return try {
            val authResult = syncAuthContext()
            val user = authResult.getOrElse { err ->
                if (err is CancellationException) throw err
                val failureMsg = err.message ?: "Failed to verify session context with backend."
                val failedStep = SyncStepResult("auth_context", required = true, succeeded = false, errorMessage = failureMsg)
                _workspaceSyncStateFlow.value = WorkspaceSyncState(
                    status = SyncHealthStatus.FAILED,
                    activeWorkspace = _workspaceSyncStateFlow.value.activeWorkspace,
                    isStaleCache = true,
                    lastSuccessfulSyncEpochMillis = previousSuccessTimestamp,
                    lastAttemptEpochMillis = attemptStartedAt,
                    errorMessage = failureMsg,
                    failedSteps = listOf(failedStep),
                    succeededSteps = emptyList(),
                    canRetry = true
                )
                return Result.failure(err)
            }
            stepResults.add(SyncStepResult("auth_context", required = true, succeeded = true))

            val authorized = user.authorizedWorkspaces()
            if (authorized.isEmpty()) {
                val secErr = SecurityException("No authorized workspace is available for this account.")
                _workspaceSyncStateFlow.value = WorkspaceSyncState(
                    status = SyncHealthStatus.FAILED,
                    activeWorkspace = null,
                    isStaleCache = true,
                    lastSuccessfulSyncEpochMillis = previousSuccessTimestamp,
                    lastAttemptEpochMillis = attemptStartedAt,
                    errorMessage = secErr.message,
                    failedSteps = listOf(SyncStepResult("workspace_authorization", true, false, secErr.message)),
                    succeededSteps = listOf("auth_context"),
                    canRetry = true
                )
                return Result.failure(secErr)
            }

            val preferredWorkspace = getActiveWorkspaceFlow().first()
            val activeWorkspace = if (authorized.contains(preferredWorkspace)) preferredWorkspace else authorized.first()

            when (activeWorkspace) {
                WorkspaceType.STOCKIST -> {
                    runStep("stockist_feed", required = true) { syncStockistFeed() }
                    runStep("stockist_inventory", required = true) { syncStockistPurchasedInventory() }
                }
                WorkspaceType.MANUFACTURER -> {
                    runStep("manufacturer_banners", required = true) { syncManufacturerBanners() }
                    runStep("promotional_campaigns", required = true) { syncPromotionalCampaigns() }
                    runStep("medicine_announcements", required = true) { syncManufacturerAnnouncements() }
                    runStep("medicine_submissions", required = true) { syncMedicineSubmissions() }
                    runStep("company_catalogue", required = true) { syncCompanyCatalogue() }
                }
                WorkspaceType.COMPANY -> {
                    runStep("company_catalogue", required = true) { syncCompanyCatalogue() }
                    runStep("medicine_submissions", required = true) { syncMedicineSubmissions() }
                    runStep("approval_audit_logs", required = false) { syncApprovalAuditLogs() }
                    runStep("master_data", required = true) { syncMasterData() }
                    if (AccessControlPolicy.canManageOrganizationRoles(user, user.organizationId).isSuccess) {
                        runStep("organization_members", required = true) { syncOrganizationMembers() }
                    }
                }
                WorkspaceType.ADMIN -> {
                    runStep("company_catalogue", required = true) { syncCompanyCatalogue() }
                    runStep("medicine_submissions", required = true) { syncMedicineSubmissions() }
                    runStep("approval_audit_logs", required = false) { syncApprovalAuditLogs() }
                    runStep("master_data", required = true) { syncMasterData() }
                    runStep("organization_members", required = true) { syncOrganizationMembers() }
                }
            }

            val failedSteps = stepResults.filter { !it.succeeded }
            val succeededNames = stepResults.filter { it.succeeded }.map { it.stepName }
            val completedAt = System.currentTimeMillis()

            if (failedSteps.isEmpty()) {
                _workspaceSyncStateFlow.value = WorkspaceSyncState(
                    status = SyncHealthStatus.SUCCESS,
                    activeWorkspace = activeWorkspace,
                    isStaleCache = false,
                    lastSuccessfulSyncEpochMillis = completedAt,
                    lastAttemptEpochMillis = attemptStartedAt,
                    errorMessage = null,
                    failedSteps = emptyList(),
                    succeededSteps = succeededNames,
                    canRetry = false
                )
                Result.success(Unit)
            } else {
                val nonAuthSuccessCount = stepResults.count { it.stepName != "auth_context" && it.succeeded }
                val summaryStatus = if (nonAuthSuccessCount > 0) {
                    SyncHealthStatus.PARTIAL_SUCCESS
                } else {
                    SyncHealthStatus.FAILED
                }
                val summaryError = failedSteps.joinToString("; ") { step ->
                    "${step.stepName}: ${step.errorMessage ?: "sync failed"}"
                }
                _workspaceSyncStateFlow.value = WorkspaceSyncState(
                    status = summaryStatus,
                    activeWorkspace = activeWorkspace,
                    isStaleCache = true,
                    lastSuccessfulSyncEpochMillis = previousSuccessTimestamp,
                    lastAttemptEpochMillis = attemptStartedAt,
                    errorMessage = summaryError,
                    failedSteps = failedSteps,
                    succeededSteps = succeededNames,
                    canRetry = true
                )
                Result.failure(IllegalStateException("Workspace sync incomplete ($summaryStatus): $summaryError"))
            }
        } catch (ce: CancellationException) {
            _workspaceSyncStateFlow.value = _workspaceSyncStateFlow.value.copy(
                status = SyncHealthStatus.CANCELLED,
                isStaleCache = true,
                errorMessage = "Sync cancelled.",
                canRetry = true
            )
            throw ce
        } finally {
            activeSyncJobRef.compareAndSet(currentJob, null)
        }
    }
}
