package com.example.data.remote.source

import com.example.data.remote.dto.ApprovalDecisionRequestDto
import com.example.data.remote.dto.AssignMemberRolesRequestDto
import com.example.data.remote.dto.AuthContextDto
import com.example.data.remote.dto.MasterDataDto
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
import java.util.UUID

/**
 * Stateless HTTP remote data source for all PharmaOS Role-Based Workspace modules.
 * Strictly fails when backend URL is unconfigured or HTTP returns an error (400/401/403/404/409/500).
 */
class WorkspaceRemoteDataSource(
    private val networkModule: NetworkModule
) {
    private fun <T> notConfigured(): NetworkResult<T> = NetworkResult.NotConfigured(
        "Spring Boot backend URL is not configured (${networkModule.getCurrentBaseUrl()})."
    )

    private fun newIdempotencyKey(prefix: String): String = "$prefix-${UUID.randomUUID()}"

    suspend fun fetchAuthContext(): NetworkResult<AuthContextDto> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.workspaceApiService().getAuthContext()
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

    suspend fun createMedicineSubmission(dto: MedicineSubmissionDto): NetworkResult<MedicineSubmissionDto> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.workspaceApiService().createMedicineSubmission(
                idempotencyKey = newIdempotencyKey("med-sub"),
                submission = dto
            )
        }
    }

    suspend fun submitMedicineForApproval(submissionId: Long): NetworkResult<MedicineSubmissionDto> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.workspaceApiService().submitMedicineForApproval(
                idempotencyKey = newIdempotencyKey("med-submit-$submissionId"),
                submissionId = submissionId
            )
        }
    }

    suspend fun approveMedicineSubmission(
        submissionId: Long,
        notes: String
    ): NetworkResult<MedicineSubmissionDto> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.workspaceApiService().approveMedicineSubmission(
                idempotencyKey = newIdempotencyKey("med-approve-$submissionId"),
                submissionId = submissionId,
                request = ApprovalDecisionRequestDto(notes = notes)
            )
        }
    }

    suspend fun rejectMedicineSubmission(
        submissionId: Long,
        reason: String
    ): NetworkResult<MedicineSubmissionDto> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.workspaceApiService().rejectMedicineSubmission(
                idempotencyKey = newIdempotencyKey("med-reject-$submissionId"),
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

    suspend fun updateCatalogueStatus(productId: Long, status: String): NetworkResult<ProductDto> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.workspaceApiService().updateCataloguePublicationStatus(
                productId = productId,
                request = UpdateCatalogueStatusRequestDto(catalogueStatus = status)
            )
        }
    }

    suspend fun createMasterData(dto: MasterDataDto): NetworkResult<MasterDataDto> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.workspaceApiService().createMasterData(
                idempotencyKey = newIdempotencyKey("master-data"),
                item = dto
            )
        }
    }

    suspend fun saveOfferBanner(dto: OfferBannerDto): NetworkResult<OfferBannerDto> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiEnvelopeCall(networkModule.moshi) {
            if (dto.id > 0L) {
                networkModule.workspaceApiService().updateOfferBanner(dto.id, dto)
            } else {
                networkModule.workspaceApiService().createOfferBanner(
                    idempotencyKey = newIdempotencyKey("banner"),
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

    suspend fun savePromotionalCampaign(dto: PromotionalCampaignDto): NetworkResult<PromotionalCampaignDto> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiEnvelopeCall(networkModule.moshi) {
            if (dto.id > 0L) {
                networkModule.workspaceApiService().updatePromotionalCampaign(dto.id, dto)
            } else {
                networkModule.workspaceApiService().createPromotionalCampaign(
                    idempotencyKey = newIdempotencyKey("campaign"),
                    campaign = dto
                )
            }
        }
    }

    suspend fun saveMedicineAnnouncement(dto: MedicineAnnouncementDto): NetworkResult<MedicineAnnouncementDto> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiEnvelopeCall(networkModule.moshi) {
            if (dto.id > 0L) {
                networkModule.workspaceApiService().updateMedicineAnnouncement(dto.id, dto)
            } else {
                networkModule.workspaceApiService().createMedicineAnnouncement(
                    idempotencyKey = newIdempotencyKey("announcement"),
                    announcement = dto
                )
            }
        }
    }

    suspend fun upsertStockistPurchasedInventory(
        dto: StockistPurchasedInventoryDto
    ): NetworkResult<StockistPurchasedInventoryDto> {
        if (!networkModule.isBackendConfigured()) return notConfigured()
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.workspaceApiService().upsertStockistPurchasedInventory(
                idempotencyKey = newIdempotencyKey("stockist-inv"),
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
}
