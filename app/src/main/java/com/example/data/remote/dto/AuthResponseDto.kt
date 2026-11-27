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
    @param:Json(name = "createdAtEpochMillis") val createdAtEpochMillis: Long = System.currentTimeMillis(),
    @param:Json(name = "emailVerified") val emailVerified: Boolean = false,
    @param:Json(name = "phoneVerified") val phoneVerified: Boolean = false,
    @param:Json(name = "profileBio") val profileBio: String? = null,
    @param:Json(name = "organizationId") val organizationId: Long? = null,
    @param:Json(name = "organizationType") val organizationType: String? = null,
    @param:Json(name = "organizationName") val organizationName: String? = null,
    @param:Json(name = "roles") val roles: List<String>? = null,
    @param:Json(name = "permissions") val permissions: List<String>? = null
) {
    fun toAuthUser(isProductionMode: Boolean = true): AuthUser {
        val strictRole = UserRole.parseStrict(role)
        val parsedStatus = AccountStatus.fromString(status, isProductionMode = isProductionMode)
        val explicitParsedRoles = roles
            ?.mapNotNull { UserRole.parseStrict(it) }
            ?.toSet()
            .orEmpty()
        val hasInvalidRolePayload = if (isProductionMode) {
            strictRole == null || (roles != null && explicitParsedRoles.isEmpty())
        } else {
            false
        }
        val parsedRole = strictRole ?: explicitParsedRoles.firstOrNull() ?: UserRole.STOCKIST
        val parsedRoles = when {
            hasInvalidRolePayload -> emptySet()
            explicitParsedRoles.isNotEmpty() -> explicitParsedRoles
            strictRole != null -> setOf(strictRole)
            else -> if (isProductionMode) emptySet() else setOf(UserRole.STOCKIST)
        }
        val resolution = if (hasInvalidRolePayload) {
            com.example.domain.model.PermissionResolution(
                permissions = emptySet(),
                status = com.example.domain.model.PermissionResolutionStatus.INVALID
            )
        } else {
            com.example.domain.model.RolePermissionMatrix.resolvePermissionState(
                roles = parsedRoles,
                rawPermissions = permissions,
                isProductionMode = isProductionMode
            )
        }
        val resolvedOrgType = com.example.domain.model.OrganizationType.fromString(
            raw = organizationType,
            fallbackRole = parsedRole,
            isProductionMode = isProductionMode
        )
        // SECURITY: Never substitute userId for a missing organizationId in production mode.
        val resolvedOrgId = if (isProductionMode) {
            if (hasInvalidRolePayload) 0L else (organizationId?.takeIf { it > 0L } ?: 0L)
        } else {
            organizationId?.takeIf { it > 0L } ?: id
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
            status = parsedStatus,
            emailVerified = emailVerified,
            phoneVerified = phoneVerified,
            profileBio = profileBio,
            organizationId = resolvedOrgId,
            organizationType = resolvedOrgType,
            organizationName = organizationName?.takeIf { it.isNotBlank() } ?: businessName,
            assignedRoles = parsedRoles,
            permissions = resolution.permissions,
            permissionStatus = resolution.status,
            isProductionContext = isProductionMode
        )
    }

    /**
     * Maps remote user into local Room cache representation.
     * Password hashes are intentionally never returned by or synced from the backend.
     */
    fun toUserEntityCache(isProductionMode: Boolean = true): UserEntity {
        val authUser = toAuthUser(isProductionMode = isProductionMode)
        val storedRole = UserRole.parseStrict(role)?.name ?: if (isProductionMode) "UNKNOWN" else authUser.role.name
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
            role = storedRole,
            status = authUser.status.name,
            createdAtEpochMillis = createdAtEpochMillis,
            emailVerified = emailVerified,
            phoneVerified = phoneVerified,
            profileBio = profileBio,
            organizationId = authUser.organizationId,
            organizationType = authUser.organizationType.name,
            organizationName = authUser.organizationName,
            assignedRolesCsv = com.example.domain.model.RolePermissionMatrix.rolesToCsv(authUser.assignedRoles),
            permissionsCsv = com.example.domain.model.PharmaPermission.toCsv(authUser.permissions)
        )
    }
}
