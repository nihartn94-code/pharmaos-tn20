package com.example.data.remote.api

import com.example.data.remote.dto.ApiResponseDto
import com.example.data.remote.dto.ComplianceVerificationDto
import com.example.data.remote.dto.UpdateUserStatusRequestDto
import com.example.data.remote.dto.UserDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * [PROPOSED — AWAITING BACKEND CONFIRMATION]
 * User profile, admin governance, and regulatory compliance REST endpoints.
 */
interface UserApiService {

    @GET("api/v1/users/me")
    suspend fun getCurrentUser(): Response<ApiResponseDto<UserDto>>

    @GET("api/v1/users")
    suspend fun getAllUsers(): Response<ApiResponseDto<List<UserDto>>>

    @PATCH("api/v1/users/{userId}/status")
    suspend fun updateUserStatus(
        @Path("userId") userId: Long,
        @Body request: UpdateUserStatusRequestDto
    ): Response<ApiResponseDto<UserDto>>

    @GET("api/v1/compliance/{userId}")
    suspend fun getComplianceVerification(
        @Path("userId") userId: Long
    ): Response<ApiResponseDto<ComplianceVerificationDto>>

    @PUT("api/v1/compliance")
    suspend fun upsertComplianceVerification(
        @Body request: ComplianceVerificationDto
    ): Response<ApiResponseDto<ComplianceVerificationDto>>
}
