package com.example.domain.repository

import com.example.data.local.entity.ProductEntity
import com.example.domain.model.AnnouncementStatus
import com.example.domain.model.ApprovalAuditEntry
import com.example.domain.model.AuthUser
import com.example.domain.model.BannerStatus
import com.example.domain.model.CampaignStatus
import com.example.domain.model.CataloguePublicationStatus
import com.example.domain.model.CompanyDashboardSummary
import com.example.domain.model.ManufacturerDashboardSummary
import com.example.domain.model.MasterDataItem
import com.example.domain.model.MasterDataType
import com.example.domain.model.MedicineAnnouncement
import com.example.domain.model.MedicineDuplicateCandidate
import com.example.domain.model.MedicineSubmission
import com.example.domain.model.OfferBanner
import com.example.domain.model.OrganizationMember
import com.example.domain.model.PromotionalCampaign
import com.example.domain.model.StockistPurchasedInventoryItem
import com.example.domain.model.UserRole
import com.example.domain.model.WorkspaceSyncState
import com.example.domain.model.WorkspaceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain repository for PharmaOS role-based workspaces:
 * - Unified Organization + Membership + Workspace Switching
 * - Compulsory Medicine Approval Workflow & Audit History
 * - Company Catalogue & Master Data Management
 * - Manufacturer Offer Banners, Promotional Campaigns & Medicine Announcements
 * - Stockist Active Promotional Feed & Isolated Stockist Purchased Inventory
 */
interface WorkspaceRepository {
    // Workspace Selection & Session Context
    fun getActiveWorkspaceFlow(): Flow<WorkspaceType>
    suspend fun switchActiveWorkspace(target: WorkspaceType): Result<WorkspaceType>
    suspend fun ensureWorkspaceSeededForLocalDev()
    fun getOrganizationMembersFlow(): Flow<List<OrganizationMember>>
    suspend fun assignOrganizationMemberRoles(memberId: Long, roles: Set<UserRole>): Result<OrganizationMember>

    // Dashboard Summaries
    fun getManufacturerDashboardSummaryFlow(): Flow<ManufacturerDashboardSummary>
    fun getCompanyDashboardSummaryFlow(): Flow<CompanyDashboardSummary>

    // Medicine Submissions & Compulsory Approval Workflow
    fun getOrganizationSubmissionsFlow(): Flow<List<MedicineSubmission>>
    fun getAuditLogsForOrganizationFlow(): Flow<List<ApprovalAuditEntry>>
    suspend fun detectDuplicateMedicines(
        medicineName: String,
        brandName: String,
        saltComposition: String,
        strength: String,
        dosageForm: String
    ): List<MedicineDuplicateCandidate>
    suspend fun saveMedicineDraft(submission: MedicineSubmission): Result<MedicineSubmission>
    suspend fun submitMedicineForApproval(submission: MedicineSubmission): Result<MedicineSubmission>
    suspend fun resubmitRejectedMedicine(submission: MedicineSubmission): Result<MedicineSubmission>
    suspend fun withdrawMedicineSubmission(submissionId: Long): Result<MedicineSubmission>
    suspend fun approveMedicineSubmission(
        submissionId: Long,
        reviewNotes: String = "",
        allowSelfApproval: Boolean = false
    ): Result<MedicineSubmission>
    suspend fun rejectMedicineSubmission(
        submissionId: Long,
        rejectionReason: String
    ): Result<MedicineSubmission>

    // Company Approved Catalogue Management (No hard delete of referenced medicines)
    fun getCompanyCatalogueProductsFlow(): Flow<List<ProductEntity>>
    suspend fun updateCompanyCatalogueProduct(product: ProductEntity): Result<Unit>
    suspend fun updateCataloguePublicationStatus(
        productId: Long,
        newStatus: CataloguePublicationStatus
    ): Result<Unit>

    // Company Master Data Management
    fun getMasterDataFlow(): Flow<List<MasterDataItem>>
    suspend fun addMasterDataItem(
        type: MasterDataType,
        name: String,
        code: String,
        description: String,
        isOrganizationScoped: Boolean
    ): Result<MasterDataItem>

    // Manufacturer Offer Banners
    fun getManufacturerBannersFlow(): Flow<List<OfferBanner>>
    suspend fun saveOfferBanner(banner: OfferBanner): Result<OfferBanner>
    suspend fun updateOfferBannerStatus(bannerId: Long, newStatus: BannerStatus): Result<OfferBanner>
    suspend fun deleteOfferBanner(bannerId: Long): Result<Unit>

    // Manufacturer Promotional Campaigns
    fun getManufacturerCampaignsFlow(): Flow<List<PromotionalCampaign>>
    suspend fun savePromotionalCampaign(campaign: PromotionalCampaign): Result<PromotionalCampaign>
    suspend fun updateCampaignStatus(campaignId: Long, newStatus: CampaignStatus): Result<PromotionalCampaign>

    // Manufacturer Medicine Announcements (Linked strictly to org's approved medicines)
    fun getManufacturerAnnouncementsFlow(): Flow<List<MedicineAnnouncement>>
    fun getApprovedProductsForOrganizationFlow(): Flow<List<ProductEntity>>
    suspend fun saveMedicineAnnouncement(announcement: MedicineAnnouncement): Result<MedicineAnnouncement>
    suspend fun updateAnnouncementStatus(
        announcementId: Long,
        newStatus: AnnouncementStatus
    ): Result<MedicineAnnouncement>

    // Stockist Dynamic Promotional Feed (Eligible Active Banners & Published Announcements)
    fun getStockistActiveBannersFlow(): Flow<List<OfferBanner>>
    fun getStockistPublishedAnnouncementsFlow(): Flow<List<MedicineAnnouncement>>

    // Stockist Purchased Inventory (Strictly Isolated from Manufacturer Real-Time Inventory)
    fun getStockistPurchasedInventoryFlow(): Flow<List<StockistPurchasedInventoryItem>>
    suspend fun upsertStockistPurchasedInventory(
        item: StockistPurchasedInventoryItem
    ): Result<StockistPurchasedInventoryItem>
    suspend fun adjustStockistPurchasedQuantity(
        itemId: Long,
        newRemainingQuantity: Int
    ): Result<StockistPurchasedInventoryItem>
    suspend fun deleteStockistPurchasedInventory(itemId: Long): Result<Unit>

    // Cache isolation helper called on logout/account switch
    suspend fun clearSensitiveLocalSessionCaches(previousUser: AuthUser?)

    // =========================================================================
    // Remote Read Synchronization (Stateless Spring Boot -> Local Room Cache)
    // =========================================================================
    suspend fun syncAuthContext(): Result<AuthUser>
    suspend fun syncOrganizationMembers(): Result<Unit>
    suspend fun syncMedicineSubmissions(): Result<Unit>
    suspend fun syncApprovalAuditLogs(submissionId: Long? = null): Result<Unit>
    suspend fun syncCompanyCatalogue(): Result<Unit>
    suspend fun syncMasterData(): Result<Unit>
    suspend fun syncManufacturerBanners(): Result<Unit>
    suspend fun syncPromotionalCampaigns(): Result<Unit>
    suspend fun syncManufacturerAnnouncements(): Result<Unit>
    suspend fun syncStockistFeed(): Result<Unit>
    suspend fun syncStockistPurchasedInventory(): Result<Unit>
    val workspaceSyncStateFlow: StateFlow<WorkspaceSyncState>
    suspend fun syncActiveWorkspace(): Result<Unit>
    fun cancelActiveSync()
    fun refreshBannerClock(nowEpochMillis: Long = System.currentTimeMillis())
}
