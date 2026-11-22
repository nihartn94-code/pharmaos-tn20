package com.example.data.remote.api

import com.example.data.remote.dto.AdjustBatchStockRequestDto
import com.example.data.remote.dto.AdjustInventoryStockRequestDto
import com.example.data.remote.dto.ApiResponseDto
import com.example.data.remote.dto.BatchDto
import com.example.data.remote.dto.CreateRecallRequestDto
import com.example.data.remote.dto.ProductInventoryDto
import com.example.data.remote.dto.RecallDto
import com.example.data.remote.dto.ShortageAlertDto
import com.example.data.remote.dto.UpdateReorderThresholdRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Inventory SKU stock tracking, batch expiry management, shortage risk alerts, and recalls.
 *
 * Role-Based Access Control (Backend Enforcement Contract):
 * Real-Time Inventory & Batch endpoints (`/api/v1/inventory` and `/api/v1/batches`) are restricted
 * exclusively to authenticated Brand (`PHARMA_COMPANY`) and Manufacturer (`MANUFACTURER`) accounts:
 *   `@PreAuthorize("hasAnyRole('MANUFACTURER', 'PHARMA_COMPANY')")`
 * Requests authenticated with `STOCKIST` tokens MUST be rejected with HTTP 403 Forbidden.
 */
interface InventoryApiService {

    // SKU Inventory (MANUFACTURER / PHARMA_COMPANY only)
    @GET("api/v1/inventory")
    suspend fun getInventory(
        @Query("lowStockOnly") lowStockOnly: Boolean = false
    ): Response<ApiResponseDto<List<ProductInventoryDto>>>

    @GET("api/v1/inventory/{sku}")
    suspend fun getInventoryBySku(
        @Path("sku") sku: String
    ): Response<ApiResponseDto<ProductInventoryDto>>

    @POST("api/v1/inventory")
    suspend fun upsertInventoryItem(
        @Body request: ProductInventoryDto
    ): Response<ApiResponseDto<ProductInventoryDto>>

    @PATCH("api/v1/inventory/{sku}/stock")
    suspend fun adjustInventoryStock(
        @Path("sku") sku: String,
        @Body request: AdjustInventoryStockRequestDto
    ): Response<ApiResponseDto<ProductInventoryDto>>

    @PATCH("api/v1/inventory/{sku}/threshold")
    suspend fun updateReorderThreshold(
        @Path("sku") sku: String,
        @Body request: UpdateReorderThresholdRequestDto
    ): Response<ApiResponseDto<ProductInventoryDto>>

    @DELETE("api/v1/inventory/{sku}")
    suspend fun deleteInventoryBySku(
        @Path("sku") sku: String
    ): Response<Unit>

    // Batches
    @GET("api/v1/batches")
    suspend fun getBatches(): Response<ApiResponseDto<List<BatchDto>>>

    @POST("api/v1/batches")
    suspend fun createBatch(
        @Body request: BatchDto
    ): Response<ApiResponseDto<BatchDto>>

    @PATCH("api/v1/batches/{batchId}/stock")
    suspend fun adjustBatchStock(
        @Path("batchId") batchId: Long,
        @Body request: AdjustBatchStockRequestDto
    ): Response<ApiResponseDto<BatchDto>>

    // Shortage Alerts & Recalls
    @GET("api/v1/shortage-alerts")
    suspend fun getShortageAlerts(): Response<ApiResponseDto<List<ShortageAlertDto>>>

    @POST("api/v1/shortage-alerts/evaluate")
    suspend fun evaluateShortageRisks(): Response<ApiResponseDto<List<ShortageAlertDto>>>

    @GET("api/v1/recalls")
    suspend fun getRecalls(): Response<ApiResponseDto<List<RecallDto>>>

    @POST("api/v1/recalls")
    suspend fun createRecall(
        @Body request: CreateRecallRequestDto
    ): Response<ApiResponseDto<RecallDto>>
}
