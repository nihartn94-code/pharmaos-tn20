package com.example

import com.example.data.local.SecureTokenStorageException
import com.example.data.remote.config.AuthExecutionMode
import com.example.data.remote.dto.ApiErrorDto
import com.example.data.remote.util.ApiException
import com.example.data.remote.util.NetworkFailureType
import com.example.domain.model.AccountStatus
import com.example.domain.model.AuthUser
import com.example.domain.model.OrganizationType
import com.example.domain.model.PermissionResolutionStatus
import com.example.domain.model.RolePermissionMatrix
import com.example.domain.model.UserRole
import com.example.domain.model.WorkspaceType
import com.example.ui.screens.auth.AuthenticationState
import com.example.ui.screens.auth.LoginDiagnosticLogger
import com.example.ui.screens.auth.LoginErrorCode
import com.example.ui.screens.auth.LoginMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginDiagnosticLoggerTest {

    @Test
    fun stockistLoginLifecycle_capturesAuthenticationStatesAndMasksIdentifiers() {
        val emittedLogs = mutableListOf<String>()
        var fakeTime = 1_700_000_000_000L
        val logger = LoginDiagnosticLogger(
            clock = { fakeTime.also { fakeTime += 50L } },
            logSink = { _, _, msg -> emittedLogs += msg }
        )

        val attemptId = logger.startAttempt(
            targetRole = UserRole.STOCKIST,
            executionMode = AuthExecutionMode.DEVELOPMENT_LOCAL_MODE,
            rawIdentifier = "stockist@pharmaos.com"
        )
        assertTrue(attemptId.startsWith("STK-"))
        assertTrue(logger.authState.value is AuthenticationState.ValidatingInput)

        logger.recordAuthenticating(
            attemptId = attemptId,
            targetRole = UserRole.STOCKIST,
            executionMode = AuthExecutionMode.DEVELOPMENT_LOCAL_MODE,
            method = LoginMethod.PASSWORD,
            rawIdentifier = "stockist@pharmaos.com",
            isBackendConfigured = false,
            backendHostSummary = "localhost"
        )
        assertTrue(logger.authState.value is AuthenticationState.Authenticating)

        val stockistUser = AuthUser(
            id = 42L,
            fullName = "Rohan Sharma",
            businessName = "Apex Pharma Distributors",
            email = "stockist@pharmaos.com",
            phone = "9876543210",
            gstin = "27AABCA9988K1Z2",
            address = "Mumbai",
            role = UserRole.STOCKIST,
            status = AccountStatus.ACTIVE,
            organizationId = 2001L,
            organizationType = OrganizationType.STOCKIST,
            assignedRoles = setOf(UserRole.STOCKIST),
            permissions = RolePermissionMatrix.stockistPermissions,
            permissionStatus = PermissionResolutionStatus.CONFIRMED,
            isProductionContext = false
        )

        logger.recordCredentialsVerified(
            attemptId = attemptId,
            targetRole = UserRole.STOCKIST,
            executionMode = AuthExecutionMode.DEVELOPMENT_LOCAL_MODE,
            user = stockistUser
        )
        logger.recordWorkspaceSyncStarted(stockistUser)
        assertTrue(logger.authState.value is AuthenticationState.SyncingWorkspaceContext)

        logger.recordWorkspaceRouteResolved(stockistUser, routedDestination = "dashboard")
        val finalState = logger.authState.value as AuthenticationState.Authenticated
        assertEquals("dashboard", finalState.routedDestination)
        assertEquals(listOf(WorkspaceType.STOCKIST), finalState.authorizedWorkspaces)

        // Raw email must be masked in emitted logs and diagnostic report
        assertTrue(emittedLogs.none { it.contains("stockist@pharmaos.com") })
        assertTrue(emittedLogs.any { it.contains("st***@pharmaos.com") })
        assertEquals("***3210 (len=10)", LoginDiagnosticLogger.maskIdentifier("9876543210"))
    }

    @Test
    fun classifyError_mapsHttpNetworkLocalAndWorkspaceFailureCodesDeterministically() {
        val logger = LoginDiagnosticLogger(logSink = { _, _, _ -> })

        val unconfigured = logger.recordFailure(
            targetRole = UserRole.STOCKIST,
            executionMode = AuthExecutionMode.PRODUCTION_BACKEND_HTTP,
            throwable = ApiException("Backend URL is not configured", isNotConfigured = true)
        )
        assertEquals(LoginErrorCode.BACKEND_NOT_CONFIGURED, unconfigured.errorCode)
        assertEquals(
            LoginErrorCode.BACKEND_NOT_CONFIGURED,
            (logger.authState.value as AuthenticationState.Failed).errorCode
        )

        val http401 = logger.recordFailure(
            targetRole = UserRole.STOCKIST,
            executionMode = AuthExecutionMode.PRODUCTION_BACKEND_HTTP,
            throwable = ApiException(
                message = "Invalid credentials",
                httpCode = 401,
                apiError = ApiErrorDto(status = 401, errorCode = "INVALID_CREDENTIALS", message = "Invalid credentials")
            )
        )
        assertEquals(LoginErrorCode.HTTP_401_UNAUTHORIZED, http401.errorCode)
        assertEquals(401, http401.httpStatusCode)
        assertEquals("INVALID_CREDENTIALS", http401.backendErrorCode)

        val timeout = LoginDiagnosticLogger.classifyError(
            ApiException("Timed out", failureType = NetworkFailureType.TIMEOUT)
        )
        assertEquals(LoginErrorCode.NETWORK_TIMEOUT, timeout.errorCode)

        val roleMismatch = LoginDiagnosticLogger.classifyError(
            IllegalArgumentException("This account is registered as 'Manufacturer'. Please switch to the Manufacturer login tab.")
        )
        assertEquals(LoginErrorCode.ROLE_MISMATCH, roleMismatch.errorCode)

        val wrongPassword = LoginDiagnosticLogger.classifyError(
            IllegalArgumentException("Incorrect password entered.")
        )
        assertEquals(LoginErrorCode.LOCAL_INCORRECT_PASSWORD, wrongPassword.errorCode)

        val keystoreErr = LoginDiagnosticLogger.classifyError(
            SecureTokenStorageException("Android Keystore unavailable")
        )
        assertEquals(LoginErrorCode.KEYSTORE_STORAGE_ERROR, keystoreErr.errorCode)

        // Post-login workspace denial explains missing production permissions/orgId
        val unconfirmedStockist = AuthUser(
            id = 99L,
            fullName = "Unconfirmed Stockist",
            businessName = "Test Pharmacy",
            email = "unconfirmed@pharmaos.com",
            phone = "9876543210",
            gstin = null,
            address = "Mumbai",
            role = UserRole.STOCKIST,
            status = AccountStatus.ACTIVE,
            organizationId = 0L,
            organizationType = OrganizationType.UNKNOWN,
            assignedRoles = setOf(UserRole.STOCKIST),
            permissions = emptySet(),
            permissionStatus = PermissionResolutionStatus.MISSING,
            isProductionContext = true
        )
        logger.recordWorkspaceRouteResolved(unconfirmedStockist, routedDestination = "login")
        val deniedState = logger.authState.value as AuthenticationState.Failed
        assertEquals(LoginErrorCode.WORKSPACE_AUTHORIZATION_DENIED, deniedState.errorCode)
        assertTrue(deniedState.reason.contains("permissionStatus=MISSING"))
        assertTrue(deniedState.reason.contains("organizationId=0"))

        val report = logger.formatDiagnosticReport()
        assertTrue(report.contains("AUTH_WS_DENIED"))
        assertFalse(report.contains("Password@123"))
    }
}
