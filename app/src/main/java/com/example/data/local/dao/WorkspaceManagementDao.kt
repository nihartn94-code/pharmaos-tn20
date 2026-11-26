package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ApprovalAuditLogEntity
import com.example.data.local.entity.MasterDataEntity
import com.example.data.local.entity.MedicineAnnouncementEntity
import com.example.data.local.entity.MedicineSubmissionEntity
import com.example.data.local.entity.OfferBannerEntity
import com.example.data.local.entity.OrganizationEntity
import com.example.data.local.entity.OrganizationMemberEntity
import com.example.data.local.entity.PromotionalCampaignEntity
import com.example.data.local.entity.StockistPurchasedInventoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceManagementDao {

    // --- Organizations & Memberships ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOrganization(org: OrganizationEntity): Long

    @Query("SELECT * FROM organizations WHERE id = :orgId LIMIT 1")
    suspend fun getOrganizationById(orgId: Long): OrganizationEntity?

    @Query("SELECT * FROM organizations WHERE type = :type ORDER BY name ASC")
    suspend fun getOrganizationsByType(type: String): List<OrganizationEntity>

    @Query("SELECT * FROM organizations ORDER BY id ASC")
    fun getAllOrganizationsFlow(): Flow<List<OrganizationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMember(member: OrganizationMemberEntity): Long

    @Query("SELECT * FROM organization_members WHERE organizationId = :orgId AND isActive = 1 ORDER BY fullName ASC")
    fun getOrganizationMembersFlow(orgId: Long): Flow<List<OrganizationMemberEntity>>

    @Query("SELECT * FROM organization_members WHERE organizationId = :orgId AND isActive = 1")
    suspend fun getOrganizationMembers(orgId: Long): List<OrganizationMemberEntity>

    @Query("SELECT * FROM organization_members WHERE userId = :userId AND isActive = 1 LIMIT 1")
    suspend fun getMemberByUserId(userId: Long): OrganizationMemberEntity?

    // --- Medicine Submissions & Approval Audit Logs ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubmission(submission: MedicineSubmissionEntity): Long

    @Update
    suspend fun updateSubmission(submission: MedicineSubmissionEntity)

    @Query("SELECT * FROM medicine_submissions WHERE id = :id LIMIT 1")
    suspend fun getSubmissionById(id: Long): MedicineSubmissionEntity?

    @Query("SELECT * FROM medicine_submissions WHERE organizationId = :orgId ORDER BY updatedAtEpochMillis DESC")
    fun getSubmissionsByOrganizationFlow(orgId: Long): Flow<List<MedicineSubmissionEntity>>

    @Query("SELECT * FROM medicine_submissions WHERE organizationId = :orgId AND approvalStatus = :status ORDER BY updatedAtEpochMillis DESC")
    fun getSubmissionsByOrgAndStatusFlow(orgId: Long, status: String): Flow<List<MedicineSubmissionEntity>>

    @Query("SELECT * FROM medicine_submissions ORDER BY updatedAtEpochMillis DESC")
    fun getAllSubmissionsFlow(): Flow<List<MedicineSubmissionEntity>>

    @Query("SELECT * FROM medicine_submissions WHERE organizationId = :orgId")
    suspend fun getSubmissionsByOrganization(orgId: Long): List<MedicineSubmissionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(entry: ApprovalAuditLogEntity): Long

    @Query("SELECT * FROM approval_audit_logs WHERE organizationId = :orgId ORDER BY timestampEpochMillis DESC")
    fun getAuditLogsByOrganizationFlow(orgId: Long): Flow<List<ApprovalAuditLogEntity>>

    @Query("SELECT * FROM approval_audit_logs WHERE submissionId = :submissionId ORDER BY timestampEpochMillis DESC")
    fun getAuditLogsForSubmissionFlow(submissionId: Long): Flow<List<ApprovalAuditLogEntity>>

    // --- Master Data ---
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMasterData(item: MasterDataEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMasterDataIgnoreDuplicate(item: MasterDataEntity): Long

    @Update
    suspend fun updateMasterData(item: MasterDataEntity)

    @Query("SELECT * FROM master_data_entries WHERE (organizationId = 0 OR organizationId = :orgId) AND isActive = 1 ORDER BY type ASC, name ASC")
    fun getMasterDataForOrganizationFlow(orgId: Long): Flow<List<MasterDataEntity>>

    @Query("SELECT * FROM master_data_entries WHERE type = :type AND normalizedName = :normalizedName AND (organizationId = 0 OR organizationId = :orgId) LIMIT 1")
    suspend fun findDuplicateMasterData(type: String, normalizedName: String, orgId: Long): MasterDataEntity?

    @Query("SELECT COUNT(*) FROM master_data_entries")
    suspend fun getMasterDataCount(): Int

    // --- Offer Banners ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBanner(banner: OfferBannerEntity): Long

    @Update
    suspend fun updateBanner(banner: OfferBannerEntity)

    @Query("DELETE FROM offer_banners WHERE id = :bannerId AND organizationId = :orgId")
    suspend fun deleteBanner(bannerId: Long, orgId: Long)

    @Query("SELECT * FROM offer_banners WHERE id = :bannerId LIMIT 1")
    suspend fun getBannerById(bannerId: Long): OfferBannerEntity?

    @Query("SELECT * FROM offer_banners WHERE organizationId = :orgId ORDER BY priorityOrder ASC, updatedAtEpochMillis DESC")
    fun getBannersByOrganizationFlow(orgId: Long): Flow<List<OfferBannerEntity>>

    @Query("SELECT * FROM offer_banners WHERE status IN ('ACTIVE', 'SCHEDULED') ORDER BY priorityOrder ASC, updatedAtEpochMillis DESC")
    fun getCandidatePublicBannersFlow(): Flow<List<OfferBannerEntity>>

    @Query("SELECT COUNT(*) FROM offer_banners")
    suspend fun getBannerCount(): Int

    // --- Promotional Campaigns ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCampaign(campaign: PromotionalCampaignEntity): Long

    @Update
    suspend fun updateCampaign(campaign: PromotionalCampaignEntity)

    @Query("SELECT * FROM promotional_campaigns WHERE id = :id LIMIT 1")
    suspend fun getCampaignById(id: Long): PromotionalCampaignEntity?

    @Query("SELECT * FROM promotional_campaigns WHERE organizationId = :orgId ORDER BY createdAtEpochMillis DESC")
    fun getCampaignsByOrganizationFlow(orgId: Long): Flow<List<PromotionalCampaignEntity>>

    @Query("SELECT * FROM promotional_campaigns WHERE status = 'ACTIVE' ORDER BY createdAtEpochMillis DESC")
    fun getActiveCampaignsFlow(): Flow<List<PromotionalCampaignEntity>>

    // --- Medicine Announcements ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnouncement(announcement: MedicineAnnouncementEntity): Long

    @Update
    suspend fun updateAnnouncement(announcement: MedicineAnnouncementEntity)

    @Query("SELECT * FROM medicine_announcements WHERE id = :id LIMIT 1")
    suspend fun getAnnouncementById(id: Long): MedicineAnnouncementEntity?

    @Query("SELECT * FROM medicine_announcements WHERE organizationId = :orgId ORDER BY createdAtEpochMillis DESC")
    fun getAnnouncementsByOrganizationFlow(orgId: Long): Flow<List<MedicineAnnouncementEntity>>

    @Query("SELECT * FROM medicine_announcements WHERE status = 'PUBLISHED' ORDER BY publishedAtEpochMillis DESC")
    fun getPublishedAnnouncementsFlow(): Flow<List<MedicineAnnouncementEntity>>

    // --- Stockist Purchased Inventory (Strictly Isolated per Stockist Org) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStockistInventoryItem(item: StockistPurchasedInventoryEntity): Long

    @Update
    suspend fun updateStockistInventoryItem(item: StockistPurchasedInventoryEntity)

    @Query("SELECT * FROM stockist_purchased_inventory WHERE id = :id AND stockistOrganizationId = :stockistOrgId LIMIT 1")
    suspend fun getStockistInventoryItemById(id: Long, stockistOrgId: Long): StockistPurchasedInventoryEntity?

    @Query("SELECT * FROM stockist_purchased_inventory WHERE stockistOrganizationId = :stockistOrgId ORDER BY updatedAtEpochMillis DESC")
    fun getStockistPurchasedInventoryFlow(stockistOrgId: Long): Flow<List<StockistPurchasedInventoryEntity>>

    @Query("DELETE FROM stockist_purchased_inventory WHERE id = :id AND stockistOrganizationId = :stockistOrgId")
    suspend fun deleteStockistInventoryItem(id: Long, stockistOrgId: Long)

    @Query("DELETE FROM stockist_purchased_inventory WHERE stockistOrganizationId = :stockistOrgId")
    suspend fun clearStockistInventoryForOrg(stockistOrgId: Long)
}
