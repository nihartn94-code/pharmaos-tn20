package com.example.data.remote.api

import com.example.data.remote.dto.AddBasketItemRequestDto
import com.example.data.remote.dto.ApiResponseDto
import com.example.data.remote.dto.BasketDto
import com.example.data.remote.dto.BasketLoadReportDto
import com.example.data.remote.dto.NamedBasketDto
import com.example.data.remote.dto.OptimizationResultDto
import com.example.data.remote.dto.OptimizeBasketRequestDto
import com.example.data.remote.dto.SaveNamedBasketRequestDto
import com.example.data.remote.dto.UpdateBasketQtyRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * [PROPOSED — AWAITING BACKEND CONFIRMATION]
 * Shopping basket, saved baskets, and Best-Buy basket optimization REST endpoints.
 */
interface BasketApiService {

    @GET("api/v1/basket")
    suspend fun getActiveBasket(): Response<ApiResponseDto<BasketDto>>

    @POST("api/v1/basket/items")
    suspend fun addBasketItem(
        @Body request: AddBasketItemRequestDto
    ): Response<ApiResponseDto<BasketDto>>

    @PUT("api/v1/basket/items/{itemId}")
    suspend fun updateBasketItemQuantity(
        @Path("itemId") itemId: Long,
        @Body request: UpdateBasketQtyRequestDto
    ): Response<ApiResponseDto<BasketDto>>

    @DELETE("api/v1/basket/items/{itemId}")
    suspend fun removeBasketItem(
        @Path("itemId") itemId: Long
    ): Response<Unit>

    @DELETE("api/v1/basket")
    suspend fun clearBasket(): Response<Unit>

    @GET("api/v1/basket/saved")
    suspend fun getSavedBaskets(): Response<ApiResponseDto<List<NamedBasketDto>>>

    @POST("api/v1/basket/saved")
    suspend fun saveNamedBasket(
        @Body request: SaveNamedBasketRequestDto
    ): Response<ApiResponseDto<NamedBasketDto>>

    @POST("api/v1/basket/saved/{savedBasketId}/load")
    suspend fun loadSavedBasket(
        @Path("savedBasketId") savedBasketId: Long
    ): Response<ApiResponseDto<BasketLoadReportDto>>

    @POST("api/v1/basket/optimize")
    suspend fun optimizeBasket(
        @Body request: OptimizeBasketRequestDto
    ): Response<ApiResponseDto<OptimizationResultDto>>

    @POST("api/v1/basket/optimize/apply")
    suspend fun applyOptimization(
        @Body request: OptimizationResultDto
    ): Response<ApiResponseDto<BasketDto>>
}
