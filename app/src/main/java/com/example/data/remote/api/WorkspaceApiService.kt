package com.example.data.remote.api

import com.example.data.remote.dto.ApiResponseDto
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
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * [PROPOSED — AWAITING BACKEND CONFIRMATION]
 * Stateless Spring Boot REST API service for PharmaOS Role-Based Workspaces.
 *
 * All endpoints require Bearer JWT authentication via [com.example.data.remote.interceptor.AuthInterceptor].
 * Backend enforces granular permissions and organizationId ownership from JWT claims + PostgreSQL JDBC queries.
 */
interface WorkspaceApiService {

    // 1. Auth Context, Organization & Member Role Management
    @GET("api/v1/auth/context")
    suspend fun getAuthContext(): Response<ApiResponseDto<AuthContextDto>>

    @GET("api/v1/organizations/{organizationId}/members")
    suspend fun getOrganizationMembers(
        @Path("organizationId") organizationId: Long
    ): Response<ApiResponseDto<List<OrganizationMemberDto>>>

    @PATCH("api/v1/organizations/{organizationId}/members/{memberId}/roles")
    suspend fun assignMemberRoles(
        @Path("organizationId") organizationId: Long,
        @Path("memberId") memberId: Long,
        @Body request: AssignMemberRolesRequestDto
    ): Response<ApiResponseDto<OrganizationMemberDto>>

    // 2. Medicine Submissions & Compulsory Approval Workflow
    @GET("api/v1/medicines/submissions")
    suspend fun getMedicineSubmissions(
        @Query("status") status: String? = null
    ): Response<ApiResponseDto<List<MedicineSubmissionDto>>>

    @POST("api/v1/medicines/submissions")
    suspend fun createMedicineSubmission(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body submission: MedicineSubmissionDto
    ): Response<ApiResponseDto<MedicineSubmissionDto>>

    @PUT("api/v1/medicines/submissions/{submissionId}")
    suspend fun updateMedicineSubmission(
        @Path("submissionId") submissionId: Long,
        @Body submission: MedicineSubmissionDto
    ): Response<ApiResponseDto<MedicineSubmissionDto>>

    @POST("api/v1/medicines/submissions/{submissionId}/submit")
    suspend fun submitMedicineForApproval(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Path("submissionId") submissionId: Long
    ): Response<ApiResponseDto<MedicineSubmissionDto>>

    @POST("api/v1/medicines/submissions/{submissionId}/approve")
    suspend fun approveMedicineSubmission(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Path("submissionId") submissionId: Long,
        @Body request: ApprovalDecisionRequestDto
    ): Response<ApiResponseDto<MedicineSubmissionDto>>

    @POST("api/v1/medicines/submissions/{submissionId}/reject")
    suspend fun rejectMedicineSubmission(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Path("submissionId") submissionId: Long,
        @Body request: ApprovalDecisionRequestDto
    ): Response<ApiResponseDto<MedicineSubmissionDto>>

    @POST("api/v1/medicines/submissions/{submissionId}/withdraw")
    suspend fun withdrawMedicineSubmission(
        @Path("submissionId") submissionId: Long
    ): Response<ApiResponseDto<MedicineSubmissionDto>>

    @GET("api/v1/medicines/submissions/{submissionId}/audit")
    suspend fun getSubmissionAuditLogs(
        @Path("submissionId") submissionId: Long
    ): Response<ApiResponseDto<List<ApprovalAuditEntryDto>>>

    // 3. Company Approved Catalogue & Master Data Management
    @GET("api/v1/company/catalogue")
    suspend fun getCompanyCatalogue(
        @Query("status") catalogueStatus: String? = null,
        @Query("query") query: String? = null
    ): Response<ApiResponseDto<List<ProductDto>>>

    @PATCH("api/v1/company/catalogue/{productId}/status")
    suspend fun updateCataloguePublicationStatus(
        @Path("productId") productId: Long,
        @Body request: UpdateCatalogueStatusRequestDto
    ): Response<ApiResponseDto<ProductDto>>

    @GET("api/v1/company/master-data")
    suspend fun getMasterData(
        @Query("type") type: String? = null
    ): Response<ApiResponseDto<List<MasterDataDto>>>

    @POST("api/v1/company/master-data")
    suspend fun createMasterData(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body item: MasterDataDto
    ): Response<ApiResponseDto<MasterDataDto>>

    @PUT("api/v1/company/master-data/{itemId}")
    suspend fun updateMasterData(
        @Path("itemId") itemId: Long,
        @Body item: MasterDataDto
    ): Response<ApiResponseDto<MasterDataDto>>

    // 4. Manufacturer Offer Banners, Promotional Campaigns & Medicine Announcements
    @GET("api/v1/manufacturer/banners")
    suspend fun getManufacturerBanners(): Response<ApiResponseDto<List<OfferBannerDto>>>

    @POST("api/v1/manufacturer/banners")
    suspend fun createOfferBanner(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body banner: OfferBannerDto
    ): Response<ApiResponseDto<OfferBannerDto>>

    @PUT("api/v1/manufacturer/banners/{bannerId}")
    suspend fun updateOfferBanner(
        @Path("bannerId") bannerId: Long,
        @Body banner: OfferBannerDto
    ): Response<ApiResponseDto<OfferBannerDto>>

    @PATCH("api/v1/manufacturer/banners/{bannerId}/status")
    suspend fun updateOfferBannerStatus(
        @Path("bannerId") bannerId: Long,
        @Body request: UpdateBannerStatusRequestDto
    ): Response<ApiResponseDto<OfferBannerDto>>

    @DELETE("api/v1/manufacturer/banners/{bannerId}")
    suspend fun deleteOfferBanner(
        @Path("bannerId") bannerId: Long
    ): Response<Unit>

    @GET("api/v1/manufacturer/campaigns")
    suspend fun getManufacturerCampaigns(): Response<ApiResponseDto<List<PromotionalCampaignDto>>>

    @POST("api/v1/manufacturer/campaigns")
    suspend fun createPromotionalCampaign(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body campaign: PromotionalCampaignDto
    ): Response<ApiResponseDto<PromotionalCampaignDto>>

    @PUT("api/v1/manufacturer/campaigns/{campaignId}")
    suspend fun updatePromotionalCampaign(
        @Path("campaignId") campaignId: Long,
        @Body campaign: PromotionalCampaignDto
    ): Response<ApiResponseDto<PromotionalCampaignDto>>

    @GET("api/v1/manufacturer/announcements")
    suspend fun getManufacturerAnnouncements(): Response<ApiResponseDto<List<MedicineAnnouncementDto>>>

    @POST("api/v1/manufacturer/announcements")
    suspend fun createMedicineAnnouncement(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body announcement: MedicineAnnouncementDto
    ): Response<ApiResponseDto<MedicineAnnouncementDto>>

    @PUT("api/v1/manufacturer/announcements/{announcementId}")
    suspend fun updateMedicineAnnouncement(
        @Path("announcementId") announcementId: Long,
        @Body announcement: MedicineAnnouncementDto
    ): Response<ApiResponseDto<MedicineAnnouncementDto>>

    // 5. Stockist Promotional Feed & Strictly Isolated Purchased Inventory
    @GET("api/v1/stockist/feed/banners")
    suspend fun getStockistActiveBanners(): Response<ApiResponseDto<List<OfferBannerDto>>>

    @GET("api/v1/stockist/feed/announcements")
    suspend fun getStockistPublishedAnnouncements(): Response<ApiResponseDto<List<MedicineAnnouncementDto>>>

    @GET("api/v1/stockist/purchased-inventory")
    suspend fun getStockistPurchasedInventory(): Response<ApiResponseDto<List<StockistPurchasedInventoryDto>>>

    @POST("api/v1/stockist/purchased-inventory")
    suspend fun upsertStockistPurchasedInventory(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body item: StockistPurchasedInventoryDto
    ): Response<ApiResponseDto<StockistPurchasedInventoryDto>>

    @DELETE("api/v1/stockist/purchased-inventory/{itemId}")
    suspend fun deleteStockistPurchasedInventory(
        @Path("itemId") itemId: Long
    ): Response<Unit>

    // 6. Media Upload (Banner Creative / Medicine Pack Photo)
    @Multipart
    @POST("api/v1/media/upload")
    suspend fun uploadMediaAsset(
        @Part file: MultipartBody.Part
    ): Response<ApiResponseDto<MediaUploadResponseDto>>
}
