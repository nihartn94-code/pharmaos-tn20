package com.example.data.remote.source

import com.example.data.remote.dto.ApprovalAuditEntryDto
import com.example.data.remote.dto.ApprovalDecisionRequestDto
import com.example.data.remote.dto.AssignMemberRolesRequestDto
import com.example.data.remote.dto.AuthContextDto
import com.example.data.remote.dto.MasterDataDto
import com.example.data.remote.dto.MediaUploadResponseDto
import com.example.data.remote.dto.MedicineAnnouncementDto
import com.example.data.remote.dto.MedicineSubmissionDto
import com.example.data.remote.dto.OfferBannerDto
import com.example.data.remote.dto.OrganizationMemberDto
import com.example.data.remote.dto.ProductDto
import com.example.data.remote.dto.PromotionalCampaignDto
import com.example.data.remote.dto.StockistPurchasedInventoryDto
import com.example.data.remote.dto.UpdateBannerStatusRequestDto
import com.example.data.remote.dto.UpdateCatalogueStatusRequestDto
import com.example.data.remote.util.NetworkResult
import com.example.data.remote.util.safeApiCall
import com.example.data.remote.util.safeApiEnvelopeCall
import com.example.di.NetworkModule
import okhttp3.MultipartBody
import java.util.UUID

/**
 * Stateless HTTP remote data source for all PharmaOS Role-Based Workspace modules.
 *
 * Implements full remote READ synchronization, edit-before-resubmit mutations,
 * multipart media uploads, and caller-supplied operation-scoped idempotency keys.
 * Strictly fails when backend URL is unconfigured or HTTP returns an error (400/401/403/404/409/500).
 */
class WorkspaceRemoteDataSource(
    private val networkModule: NetworkModule
) {
    private fun <T> notConfigured(): NetworkResult<T> = NetworkResult.NotConfigured(
        "Spring Boot backend URL is not configured (${networkModule.getCurrentBaseUrl()})."
    )

    private fun resolveIdempotencyKey(provided: String?, prefix: String): String =
        provided?.takeIf { it.isNotBlank() } ?: "$prefix-${UUID.randomUUID()}"

    // =========================================================================
    // 1. Auth Context & Organization Members
    // =========================================================================

    suspend fun fetchAuthContext(): NetworkResult<AuthContextDto> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.workspaceApiService().getAuthContext()
        }
    }

    suspend fun fetchOrganizationMembers(organizationId: Long): NetworkResult<List<OrganizationMemberDto>> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.workspaceApiService().getOrganizationMembers(organizationId)
        }
    }

    suspend fun assignMemberRoles(
        organizationId: Long,
        memberId: Long,
        roles: List<String>,
        permissions: List<String>? = null
    ): NetworkResult<OrganizationMemberDto> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.workspaceApiService().assignMemberRoles(
                organizationId = organizationId,
                memberId = memberId,
                request = AssignMemberRolesRequestDto(roles = roles, permissions = permissions)
            )
        }
    }

    // =========================================================================
    // 2. Medicine Submissions, Edit-Before-Resubmit & Audit Logs
    // =========================================================================

    suspend fun fetchMedicineSubmissions(status: String? = null): NetworkResult<List<MedicineSubmissionDto>> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.workspaceApiService().getMedicineSubmissions(status = status)
        }
    }

    suspend fun fetchSubmissionAuditLogs(submissionId: Long): NetworkResult<List<ApprovalAuditEntryDto>> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.workspaceApiService().getSubmissionAuditLogs(submissionId)
        }
    }

    suspend fun createMedicineSubmission(
        dto: MedicineSubmissionDto,
        idempotencyKey: String? = null
    ): NetworkResult<MedicineSubmissionDto> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.workspaceApiService().createMedicineSubmission(
                idempotencyKey = resolveIdempotencyKey(idempotencyKey, "med-sub"),
                submission = dto
            )
        }
    }

    suspend fun updateMedicineSubmission(
        submissionId: Long,
        dto: MedicineSubmissionDto
    ): NetworkResult<MedicineSubmissionDto> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.workspaceApiService().updateMedicineSubmission(
                submissionId = submissionId,
                submission = dto
            )
        }
    }

    suspend fun submitMedicineForApproval(
        submissionId: Long,
        idempotencyKey: String? = null
    ): NetworkResult<MedicineSubmissionDto> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.workspaceApiService().submitMedicineForApproval(
                idempotencyKey = resolveIdempotencyKey(idempotencyKey, "med-submit-$submissionId"),
                submissionId = submissionId
            )
        }
    }

    suspend fun approveMedicineSubmission(
        submissionId: Long,
        notes: String,
        idempotencyKey: String? = null
    ): NetworkResult<MedicineSubmissionDto> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.workspaceApiService().approveMedicineSubmission(
                idempotencyKey = resolveIdempotencyKey(idempotencyKey, "med-approve-$submissionId"),
                submissionId = submissionId,
                request = ApprovalDecisionRequestDto(notes = notes)
            )
        }
    }

    suspend fun rejectMedicineSubmission(
        submissionId: Long,
        reason: String,
        idempotencyKey: String? = null
    ): NetworkResult<MedicineSubmissionDto> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.workspaceApiService().rejectMedicineSubmission(
                idempotencyKey = resolveIdempotencyKey(idempotencyKey, "med-reject-$submissionId"),
                submissionId = submissionId,
                request = ApprovalDecisionRequestDto(reason = reason)
            )
        }
    }

    suspend fun withdrawMedicineSubmission(submissionId: Long): NetworkResult<MedicineSubmissionDto> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.workspaceApiService().withdrawMedicineSubmission(submissionId)
        }
    }

    // =========================================================================
    // 3. Company Catalogue & Master Data
    // =========================================================================

    suspend fun fetchCompanyCatalogue(
        status: String? = null,
        query: String? = null
    ): NetworkResult<List<ProductDto>> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.workspaceApiService().getCompanyCatalogue(
                catalogueStatus = status,
                query = query
            )
        }
    }

    suspend fun updateCatalogueStatus(productId: Long, status: String): NetworkResult<ProductDto> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.workspaceApiService().updateCataloguePublicationStatus(
                productId = productId,
                request = UpdateCatalogueStatusRequestDto(catalogueStatus = status)
            )
        }
    }

    suspend fun fetchMasterData(type: String? = null): NetworkResult<List<MasterDataDto>> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.workspaceApiService().getMasterData(type = type)
        }
    }

    suspend fun createMasterData(
        dto: MasterDataDto,
        idempotencyKey: String? = null
    ): NetworkResult<MasterDataDto> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.workspaceApiService().createMasterData(
                idempotencyKey = resolveIdempotencyKey(idempotencyKey, "master-data"),
                item = dto
            )
        }
    }

    // =========================================================================
    // 4. Manufacturer Offer Banners, Campaigns & Announcements
    // =========================================================================

    suspend fun fetchManufacturerBanners(): NetworkResult<List<OfferBannerDto>> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.workspaceApiService().getManufacturerBanners()
        }
    }

    suspend fun saveOfferBanner(
        dto: OfferBannerDto,
        idempotencyKey: String? = null
    ): NetworkResult<OfferBannerDto> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiEnvelopeCall(networkModule.moshi) {
            if (dto.id > 0L) {
                networkModule.workspaceApiService().updateOfferBanner(dto.id, dto)
            } else {
                networkModule.workspaceApiService().createOfferBanner(
                    idempotencyKey = resolveIdempotencyKey(idempotencyKey, "banner"),
                    banner = dto
                )
            }
        }
    }

    suspend fun updateOfferBannerStatus(bannerId: Long, status: String): NetworkResult<OfferBannerDto> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.workspaceApiService().updateOfferBannerStatus(
                bannerId = bannerId,
                request = UpdateBannerStatusRequestDto(status = status)
            )
        }
    }

    suspend fun deleteOfferBanner(bannerId: Long): NetworkResult<Unit> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiCall(networkModule.moshi) {
            networkModule.workspaceApiService().deleteOfferBanner(bannerId)
        }
    }

    suspend fun fetchManufacturerCampaigns(): NetworkResult<List<PromotionalCampaignDto>> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.workspaceApiService().getManufacturerCampaigns()
        }
    }

    suspend fun savePromotionalCampaign(
        dto: PromotionalCampaignDto,
        idempotencyKey: String? = null
    ): NetworkResult<PromotionalCampaignDto> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiEnvelopeCall(networkModule.moshi) {
            if (dto.id > 0L) {
                networkModule.workspaceApiService().updatePromotionalCampaign(dto.id, dto)
            } else {
                networkModule.workspaceApiService().createPromotionalCampaign(
                    idempotencyKey = resolveIdempotencyKey(idempotencyKey, "campaign"),
                    campaign = dto
                )
            }
        }
    }

    suspend fun fetchManufacturerAnnouncements(): NetworkResult<List<MedicineAnnouncementDto>> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.workspaceApiService().getManufacturerAnnouncements()
        }
    }

    suspend fun saveMedicineAnnouncement(
        dto: MedicineAnnouncementDto,
        idempotencyKey: String? = null
    ): NetworkResult<MedicineAnnouncementDto> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiEnvelopeCall(networkModule.moshi) {
            if (dto.id > 0L) {
                networkModule.workspaceApiService().updateMedicineAnnouncement(dto.id, dto)
            } else {
                networkModule.workspaceApiService().createMedicineAnnouncement(
                    idempotencyKey = resolveIdempotencyKey(idempotencyKey, "announcement"),
                    announcement = dto
                )
            }
        }
    }

    // =========================================================================
    // 5. Stockist Feed & Strictly Isolated Purchased Inventory
    // =========================================================================

    suspend fun fetchStockistActiveBanners(): NetworkResult<List<OfferBannerDto>> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.workspaceApiService().getStockistActiveBanners()
        }
    }

    suspend fun fetchStockistPublishedAnnouncements(): NetworkResult<List<MedicineAnnouncementDto>> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.workspaceApiService().getStockistPublishedAnnouncements()
        }
    }

    suspend fun fetchStockistPurchasedInventory(): NetworkResult<List<StockistPurchasedInventoryDto>> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.workspaceApiService().getStockistPurchasedInventory()
        }
    }

    suspend fun upsertStockistPurchasedInventory(
        dto: StockistPurchasedInventoryDto,
        idempotencyKey: String? = null
    ): NetworkResult<StockistPurchasedInventoryDto> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.workspaceApiService().upsertStockistPurchasedInventory(
                idempotencyKey = resolveIdempotencyKey(idempotencyKey, "stockist-inv"),
                item = dto
            )
        }
    }

    suspend fun deleteStockistPurchasedInventory(itemId: Long): NetworkResult<Unit> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiCall(networkModule.moshi) {
            networkModule.workspaceApiService().deleteStockistPurchasedInventory(itemId)
        }
    }

    // =========================================================================
    // 6. Media Upload (Multipart)
    // =========================================================================

    suspend fun uploadMediaAsset(filePart: MultipartBody.Part): NetworkResult<MediaUploadResponseDto> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.workspaceApiService().uploadMediaAsset(filePart)
        }
    }
}
