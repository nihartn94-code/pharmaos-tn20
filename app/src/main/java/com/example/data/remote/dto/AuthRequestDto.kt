package com.example.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * [PROPOSED — AWAITING BACKEND CONFIRMATION]
 * Login HTTP POST request payload.
 * Field names are provisional until confirmed by the Spring Boot backend developer.
 */
@JsonClass(generateAdapter = true)
data class AuthRequestDto(
    @param:Json(name = "identifier") val identifier: String,
    @param:Json(name = "password") val password: String,
    @param:Json(name = "expectedRole") val expectedRole: String? = null
)

/**
 * [PROPOSED — AWAITING BACKEND CONFIRMATION]
 * Google Sign-In / OAuth2 token exchange request payload.
 */
@JsonClass(generateAdapter = true)
data class GoogleAuthRequestDto(
    @param:Json(name = "idToken") val idToken: String? = null,
    @param:Json(name = "email") val email: String,
    @param:Json(name = "displayName") val displayName: String,
    @param:Json(name = "role") val role: String
)

/**
 * [PROPOSED — AWAITING BACKEND CONFIRMATION]
 * Token refresh request payload.
 */
@JsonClass(generateAdapter = true)
data class RefreshTokenRequestDto(
    @param:Json(name = "refreshToken") val refreshToken: String
)
