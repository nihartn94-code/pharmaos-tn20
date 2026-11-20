package com.example.data.remote.source

import com.example.data.remote.dto.AuthRequestDto
import com.example.data.remote.dto.AuthResponseDto
import com.example.data.remote.dto.GoogleAuthRequestDto
import com.example.data.remote.dto.RefreshTokenRequestDto
import com.example.data.remote.dto.RegisterRequestDto
import com.example.data.remote.util.NetworkResult
import com.example.data.remote.util.safeApiCall
import com.example.data.remote.util.safeApiEnvelopeCall
import com.example.di.NetworkModule

/**
 * Remote Data Source dedicated to PharmaOS authentication HTTP requests.
 *
 * Uses provisional Spring Boot REST contracts defined in PHARMAOS_API_CONTRACT.md.
 * Never invents fake HTTP successes or silently falls back to local credentials.
 */
class AuthRemoteDataSource(
    private val networkModule: NetworkModule
) {

    suspend fun login(
        identifier: String,
        password: String,
        expectedRole: String?
    ): NetworkResult<AuthResponseDto> {
        if (!networkModule.isBackendConfigured()) {
            return NetworkResult.NotConfigured(
                "Spring Boot backend URL is not configured yet (${networkModule.getCurrentBaseUrl()}). " +
                    "Configure the backend URL or enable Development Local Mode for offline testing."
            )
        }
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.authApiService().login(
                AuthRequestDto(
                    identifier = identifier.trim(),
                    password = password,
                    expectedRole = expectedRole
                )
            )
        }
    }

    suspend fun register(request: RegisterRequestDto): NetworkResult<AuthResponseDto> {
        if (!networkModule.isBackendConfigured()) {
            return NetworkResult.NotConfigured(
                "Spring Boot backend URL is not configured yet (${networkModule.getCurrentBaseUrl()}). " +
                    "Configure the backend URL or enable Development Local Mode for offline testing."
            )
        }
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.authApiService().register(request)
        }
    }

    suspend fun googleSignIn(
        email: String,
        displayName: String,
        role: String,
        idToken: String? = null
    ): NetworkResult<AuthResponseDto> {
        if (!networkModule.isBackendConfigured()) {
            return NetworkResult.NotConfigured(
                "Spring Boot backend URL is not configured yet. Configure the backend URL or enable Development Local Mode."
            )
        }
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.authApiService().googleSignIn(
                GoogleAuthRequestDto(
                    idToken = idToken,
                    email = email.trim().lowercase(),
                    displayName = displayName.trim(),
                    role = role
                )
            )
        }
    }

    suspend fun refreshToken(refreshToken: String): NetworkResult<AuthResponseDto> {
        if (!networkModule.isBackendConfigured()) {
            return NetworkResult.NotConfigured("Spring Boot backend URL is not configured.")
        }
        return safeApiEnvelopeCall(networkModule.moshi) {
            networkModule.authApiService().refreshToken(RefreshTokenRequestDto(refreshToken))
        }
    }

    suspend fun logout(): NetworkResult<Unit> {
        if (!networkModule.isBackendConfigured()) {
            return NetworkResult.NotConfigured("Spring Boot backend URL is not configured.")
        }
        return safeApiCall(networkModule.moshi) {
            networkModule.authApiService().logout()
        }
    }
}
