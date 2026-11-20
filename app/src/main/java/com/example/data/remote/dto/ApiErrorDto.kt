package com.example.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * [PROPOSED — AWAITING BACKEND CONFIRMATION]
 * Standard Spring Boot REST error payload (compatible with RFC-7807 / Spring ErrorAttributes).
 */
@JsonClass(generateAdapter = true)
data class ApiErrorDto(
    @param:Json(name = "timestamp") val timestamp: String? = null,
    @param:Json(name = "status") val status: Int? = null,
    @param:Json(name = "error") val error: String? = null,
    @param:Json(name = "errorCode") val errorCode: String? = null,
    @param:Json(name = "message") val message: String? = null,
    @param:Json(name = "path") val path: String? = null,
    @param:Json(name = "fieldErrors") val fieldErrors: Map<String, String>? = null
) {
    fun toUserFriendlyMessage(fallback: String): String {
        val validationDetails = fieldErrors
            ?.entries
            ?.joinToString("; ") { "${it.key}: ${it.value}" }
            ?.takeIf { it.isNotBlank() }

        return when {
            !message.isNullOrBlank() && !validationDetails.isNullOrBlank() -> "$message ($validationDetails)"
            !message.isNullOrBlank() -> message
            !validationDetails.isNullOrBlank() -> validationDetails
            !error.isNullOrBlank() -> error
            else -> fallback
        }
    }
}
