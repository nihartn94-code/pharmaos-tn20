package com.example.data.remote.api

import com.example.data.remote.dto.ApiResponseDto
import com.example.data.remote.dto.ProductDto
import com.example.data.remote.dto.UpdateProductStatusRequestDto
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
 * [PROPOSED — AWAITING BACKEND CONFIRMATION]
 * Product catalogue CRUD and alternative brand discovery REST endpoints.
 */
interface ProductApiService {

    @GET("api/v1/products")
    suspend fun getProducts(
        @Query("query") query: String? = null,
        @Query("activeOnly") activeOnly: Boolean = true
    ): Response<ApiResponseDto<List<ProductDto>>>

    @GET("api/v1/products/{productId}")
    suspend fun getProductById(
        @Path("productId") productId: Long
    ): Response<ApiResponseDto<ProductDto>>

    @GET("api/v1/products/{productId}/alternatives")
    suspend fun getAlternativeBrands(
        @Path("productId") productId: Long
    ): Response<ApiResponseDto<List<ProductDto>>>

    @POST("api/v1/products")
    suspend fun createProduct(
        @Body request: ProductDto
    ): Response<ApiResponseDto<ProductDto>>

    @PUT("api/v1/products/{productId}")
    suspend fun updateProduct(
        @Path("productId") productId: Long,
        @Body request: ProductDto
    ): Response<ApiResponseDto<ProductDto>>

    @PATCH("api/v1/products/{productId}/status")
    suspend fun updateProductStatus(
        @Path("productId") productId: Long,
        @Body request: UpdateProductStatusRequestDto
    ): Response<Unit>

    @DELETE("api/v1/products/{productId}")
    suspend fun deleteProduct(
        @Path("productId") productId: Long
    ): Response<Unit>
}
