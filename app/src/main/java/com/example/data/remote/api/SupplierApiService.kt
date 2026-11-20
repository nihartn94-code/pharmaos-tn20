package com.example.data.remote.api

import com.example.data.remote.dto.ApiResponseDto
import com.example.data.remote.dto.DashboardStatsDto
import com.example.data.remote.dto.SupplierOfferDto
import com.example.data.remote.dto.SupplierSettingsDto
import com.example.data.remote.dto.TrustScoreDto
import com.example.data.remote.dto.UpdateProductStatusRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * [PROPOSED — AWAITING BACKEND CONFIRMATION]
 * Supplier offers, freight settings, Pharma Trust Score, and dashboard analytics endpoints.
 */
interface SupplierApiService {

    @GET("api/v1/products/{productId}/offers")
    suspend fun getOffersForProduct(
        @Path("productId") productId: Long
    ): Response<ApiResponseDto<List<SupplierOfferDto>>>

    @GET("api/v1/suppliers/{supplierId}/offers")
    suspend fun getOffersBySupplier(
        @Path("supplierId") supplierId: Long
    ): Response<ApiResponseDto<List<SupplierOfferDto>>>

    @POST("api/v1/offers")
    suspend fun createOffer(
        @Body request: SupplierOfferDto
    ): Response<ApiResponseDto<SupplierOfferDto>>

    @PUT("api/v1/offers/{offerId}")
    suspend fun updateOffer(
        @Path("offerId") offerId: Long,
        @Body request: SupplierOfferDto
    ): Response<ApiResponseDto<SupplierOfferDto>>

    @PATCH("api/v1/offers/{offerId}/status")
    suspend fun updateOfferStatus(
        @Path("offerId") offerId: Long,
        @Body request: UpdateProductStatusRequestDto
    ): Response<Unit>

    @GET("api/v1/suppliers/{supplierId}/settings")
    suspend fun getSupplierSettings(
        @Path("supplierId") supplierId: Long
    ): Response<ApiResponseDto<SupplierSettingsDto>>

    @PUT("api/v1/suppliers/{supplierId}/settings")
    suspend fun updateSupplierSettings(
        @Path("supplierId") supplierId: Long,
        @Body request: SupplierSettingsDto
    ): Response<ApiResponseDto<SupplierSettingsDto>>

    @GET("api/v1/suppliers/{supplierId}/trust-score")
    suspend fun getSupplierTrustScore(
        @Path("supplierId") supplierId: Long
    ): Response<ApiResponseDto<TrustScoreDto>>

    @GET("api/v1/suppliers/trust-scores")
    suspend fun getSupplierTrustScoreComparison(): Response<ApiResponseDto<List<TrustScoreDto>>>

    @GET("api/v1/analytics/dashboard")
    suspend fun getDashboardAnalytics(): Response<ApiResponseDto<DashboardStatsDto>>
}
