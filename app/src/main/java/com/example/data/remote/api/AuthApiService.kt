package com.example.data.remote.api

import com.example.data.remote.dto.ApiResponseDto
import com.example.data.remote.dto.AuthRequestDto
import com.example.data.remote.dto.AuthResponseDto
import com.example.data.remote.dto.GoogleAuthRequestDto
import com.example.data.remote.dto.RefreshTokenRequestDto
import com.example.data.remote.dto.RegisterRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * [PROPOSED — AWAITING BACKEND CONFIRMATION]
 * Authentication REST API endpoints for the Spring Boot backend.
 * None of these endpoints are live until confirmed by the backend developer.
 */
interface AuthApiService {

    @POST("api/v1/auth/login")
    suspend fun login(
        @Body request: AuthRequestDto
    ): Response<ApiResponseDto<AuthResponseDto>>

    @POST("api/v1/auth/register")
    suspend fun register(
        @Body request: RegisterRequestDto
    ): Response<ApiResponseDto<AuthResponseDto>>

    @POST("api/v1/auth/google")
    suspend fun googleSignIn(
        @Body request: GoogleAuthRequestDto
    ): Response<ApiResponseDto<AuthResponseDto>>

    @POST("api/v1/auth/refresh")
    suspend fun refreshToken(
        @Body request: RefreshTokenRequestDto
    ): Response<ApiResponseDto<AuthResponseDto>>

    @POST("api/v1/auth/logout")
    suspend fun logout(): Response<Unit>
}
