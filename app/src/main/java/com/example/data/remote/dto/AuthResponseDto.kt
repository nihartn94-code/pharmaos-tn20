package com.example.data.remote.dto

import com.example.data.local.entity.UserEntity
import com.example.domain.model.AccountStatus
import com.example.domain.model.AuthUser
import com.example.domain.model.UserRole
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * [PROPOSED — AWAITING BACKEND CONFIRMATION]
 * Authentication response payload containing JWT access/refresh tokens and user profile.
 * Field names are provisional until confirmed by the Spring Boot backend developer.
 */
@JsonClass(generateAdapter = true)
data class AuthResponseDto(
    @param:Json(name = "accessToken") val accessToken: String,
    @param:Json(name = "refreshToken") val refreshToken: String? = null,
    @param:Json(name = "tokenType") val tokenType: String = "Bearer",
    @param:Json(name = "expiresInSeconds") val expiresInSeconds: Long? = null,
    @param:Json(name = "user") val user: UserDto
)

/**
 * [PROPOSED — AWAITING BACKEND CONFIRMATION]
 * Remote user representation mapped to PostgreSQL `users` table.
 */
@JsonClass(generateAdapter = true)
data class UserDto(
    @param:Json(name = "id") val id: Long,
    @param:Json(name = "fullName") val fullName: String,
    @param:Json(name = "businessName") val businessName: String,
    @param:Json(name = "email") val email: String,
    @param:Json(name = "phone") val phone: String,
    @param:Json(name = "gstin") val gstin: String? = null,
    @param:Json(name = "address") val address: String,
    @param:Json(name = "role") val role: String,
    @param:Json(name = "status") val status: String = "ACTIVE",
    @param:Json(name = "createdAtEpochMillis") val createdAtEpochMillis: Long = System.currentTimeMillis()
) {
    fun toAuthUser(): AuthUser {
        val parsedRole = try {
            UserRole.valueOf(role.uppercase())
        } catch (_: Exception) {
            UserRole.STOCKIST
        }
        val parsedStatus = try {
            AccountStatus.valueOf(status.uppercase())
        } catch (_: Exception) {
            AccountStatus.ACTIVE
        }
        return AuthUser(
            id = id,
            fullName = fullName,
            businessName = businessName,
            email = email,
            phone = phone,
            gstin = gstin,
            address = address,
            role = parsedRole,
            status = parsedStatus
        )
    }

    /**
     * Maps remote user into local Room cache representation.
     * Password hashes are intentionally never returned by or synced from the backend.
     */
    fun toUserEntityCache(): UserEntity {
        return UserEntity(
            id = id,
            fullName = fullName,
            businessName = businessName,
            email = email,
            phone = phone,
            gstin = gstin,
            address = address,
            passwordHash = "",
            passwordSalt = "",
            role = role.uppercase(),
            status = status.uppercase(),
            createdAtEpochMillis = createdAtEpochMillis
        )
    }
}
