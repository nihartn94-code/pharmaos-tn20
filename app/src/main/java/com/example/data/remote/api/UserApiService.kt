package com.example.data.remote.api

import com.example.data.remote.dto.ApiResponseDto
import com.example.data.remote.dto.ComplianceVerificationDto
import com.example.data.remote.dto.ConfirmContactVerificationRequestDto
import com.example.data.remote.dto.ContactVerificationRequestDto
import com.example.data.remote.dto.ContactVerificationStatusDto
import com.example.data.remote.dto.LegalDocumentDto
import com.example.data.remote.dto.UpdateUserProfileRequestDto
import com.example.data.remote.dto.UpdateUserStatusRequestDto
import com.example.data.remote.dto.UserAddressDto
import com.example.data.remote.dto.UserDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * User profile, address book, contact verification, legal documents, admin governance,
 * and regulatory compliance REST endpoints.
 */
interface UserApiService {

    @GET("api/v1/users/me")
    suspend fun getCurrentUser(): Response<ApiResponseDto<UserDto>>

    @PUT("api/v1/users/me")
    suspend fun updateCurrentUserProfile(
        @Body request: UpdateUserProfileRequestDto
    ): Response<ApiResponseDto<UserDto>>

    @POST("api/v1/users/me/verify-contact/request")
    suspend fun requestContactVerification(
        @Body request: ContactVerificationRequestDto
    ): Response<ApiResponseDto<ContactVerificationStatusDto>>

    @POST("api/v1/users/me/verify-contact/confirm")
    suspend fun confirmContactVerification(
        @Body request: ConfirmContactVerificationRequestDto
    ): Response<ApiResponseDto<ContactVerificationStatusDto>>

    @GET("api/v1/users/me/addresses")
    suspend fun getUserAddresses(): Response<ApiResponseDto<List<UserAddressDto>>>

    @POST("api/v1/users/me/addresses")
    suspend fun createUserAddress(
        @Body request: UserAddressDto
    ): Response<ApiResponseDto<UserAddressDto>>

    @PUT("api/v1/users/me/addresses/{addressId}")
    suspend fun updateUserAddress(
        @Path("addressId") addressId: Long,
        @Body request: UserAddressDto
    ): Response<ApiResponseDto<UserAddressDto>>

    @DELETE("api/v1/users/me/addresses/{addressId}")
    suspend fun deleteUserAddress(
        @Path("addressId") addressId: Long
    ): Response<Unit>

    @PATCH("api/v1/users/me/addresses/{addressId}/default")
    suspend fun setDefaultUserAddress(
        @Path("addressId") addressId: Long
    ): Response<ApiResponseDto<UserAddressDto>>

    @GET("api/v1/legal/documents")
    suspend fun getLegalDocuments(): Response<ApiResponseDto<List<LegalDocumentDto>>>

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
