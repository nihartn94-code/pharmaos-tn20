package com.example.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * [PROPOSED — AWAITING BACKEND CONFIRMATION]
 * User registration HTTP POST request payload.
 * Field names are provisional until confirmed by the Spring Boot backend developer.
 */
@JsonClass(generateAdapter = true)
data class RegisterRequestDto(
    @param:Json(name = "fullName") val fullName: String,
    @param:Json(name = "businessName") val businessName: String,
    @param:Json(name = "email") val email: String,
    @param:Json(name = "phone") val phone: String,
    @param:Json(name = "gstin") val gstin: String? = null,
    @param:Json(name = "address") val address: String,
    @param:Json(name = "password") val password: String,
    @param:Json(name = "role") val role: String
)
