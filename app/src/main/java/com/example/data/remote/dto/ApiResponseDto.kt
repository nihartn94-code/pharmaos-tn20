package com.example.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * [PROPOSED — AWAITING BACKEND CONFIRMATION]
 * Generic REST API response envelope proposed for Spring Boot JDBC backend.
 */
@JsonClass(generateAdapter = true)
data class ApiResponseDto<T>(
    @param:Json(name = "success") val success: Boolean = true,
    @param:Json(name = "message") val message: String? = null,
    @param:Json(name = "data") val data: T? = null,
    @param:Json(name = "timestamp") val timestamp: String? = null,
    @param:Json(name = "path") val path: String? = null
)

/**
 * [PROPOSED — AWAITING BACKEND CONFIRMATION]
 * Paginated list wrapper for large catalogue, order, or ledger queries.
 */
@JsonClass(generateAdapter = true)
data class PagedResponseDto<T>(
    @param:Json(name = "items") val items: List<T> = emptyList(),
    @param:Json(name = "page") val page: Int = 0,
    @param:Json(name = "size") val size: Int = 20,
    @param:Json(name = "totalElements") val totalElements: Long = 0L,
    @param:Json(name = "totalPages") val totalPages: Int = 0
)
