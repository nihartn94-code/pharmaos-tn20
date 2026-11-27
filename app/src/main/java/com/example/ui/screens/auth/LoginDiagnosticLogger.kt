package com.example.ui.screens.auth

import android.util.Log
import com.example.data.local.SecureTokenStorageException
import com.example.data.remote.config.AuthExecutionMode
import com.example.data.remote.util.ApiException
import com.example.data.remote.util.NetworkFailureType
import com.example.domain.model.AccountStatus
import com.example.domain.model.AuthUser
import com.example.domain.model.OrganizationType
import com.example.domain.model.PermissionResolutionStatus
import com.example.domain.model.UserRole
import com.example.domain.model.WorkspaceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Login authentication method tracked by [LoginDiagnosticLogger].
 */
enum class LoginMethod {
    PASSWORD,
    GOOGLE
}

/**
 * Structured error taxonomy capturing every failure mode across local dev and
 * stateless HTTP backend authentication, especially during the Stockist login flow.
 */
enum class LoginErrorCode(val code: String) {
    VALIDATION_EMPTY_CREDENTIALS("AUTH_VAL_001"),
    BACKEND_NOT_CONFIGURED("AUTH_CFG_001"),
    HTTP_400_BAD_REQUEST("AUTH_HTTP_400"),
    HTTP_401_UNAUTHORIZED("AUTH_HTTP_401"),
    HTTP_403_FORBIDDEN("AUTH_HTTP_403"),
    HTTP_404_NOT_FOUND("AUTH_HTTP_404"),
    HTTP_422_VALIDATION_FAILED("AUTH_HTTP_422"),
    HTTP_5XX_SERVER_ERROR("AUTH_HTTP_5XX"),
    HTTP_OTHER_ERROR("AUTH_HTTP_ERR"),
    NETWORK_TIMEOUT("AUTH_NET_TIMEOUT"),
    NETWORK_NO_INTERNET("AUTH_NET_DNS"),
    NETWORK_SERVER_UNAVAILABLE("AUTH_NET_CONN"),
    NETWORK_INVALID_JSON("AUTH_NET_JSON"),
    LOCAL_ACCOUNT_NOT_FOUND("AUTH_LOCAL_404"),
    LOCAL_INCORRECT_PASSWORD("AUTH_LOCAL_401"),
    ROLE_MISMATCH("AUTH_RBAC_ROLE"),
    ACCOUNT_SUSPENDED_OR_UNVERIFIED("AUTH_ACCT_STATUS"),
    KEYSTORE_STORAGE_ERROR("AUTH_KEYSTORE_ERR"),
    WORKSPACE_SYNC_FAILED("AUTH_SYNC_ERR"),
    WORKSPACE_AUTHORIZATION_DENIED("AUTH_WS_DENIED"),
    UNKNOWN_ERROR("AUTH_UNKNOWN")
}

/**
 * Explicit state machine representing the live authentication lifecycle of a login attempt.
 */
sealed class AuthenticationState {
    data object Idle : AuthenticationState()

    data class ValidatingInput(
        val targetRole: UserRole,
        val executionMode: AuthExecutionMode,
        val maskedIdentifier: String
    ) : AuthenticationState()

    data class Authenticating(
        val targetRole: UserRole,
        val executionMode: AuthExecutionMode,
        val method: LoginMethod,
        val maskedIdentifier: String,
        val isBackendConfigured: Boolean
    ) : AuthenticationState()

    data class SyncingWorkspaceContext(
        val targetRole: UserRole,
        val userId: Long,
        val organizationId: Long,
        val permissionStatus: PermissionResolutionStatus
    ) : AuthenticationState()

    data class Authenticated(
        val targetRole: UserRole,
        val actualRole: UserRole,
        val userId: Long,
        val organizationId: Long,
        val organizationType: OrganizationType,
        val permissionStatus: PermissionResolutionStatus,
        val authorizedWorkspaces: List<WorkspaceType>,
        val routedDestination: String? = null
    ) : AuthenticationState()

    data class Failed(
        val targetRole: UserRole,
        val executionMode: AuthExecutionMode,
        val errorCode: LoginErrorCode,
        val httpStatusCode: Int? = null,
        val backendErrorCode: String? = null,
        val reason: String
    ) : AuthenticationState()

    val shortLabel: String
        get() = when (this) {
            is Idle -> "IDLE"
            is ValidatingInput -> "VALIDATING_INPUT"
            is Authenticating -> "AUTHENTICATING"
            is SyncingWorkspaceContext -> "SYNCING_WORKSPACE"
            is Authenticated -> "AUTHENTICATED"
            is Failed -> "FAILED(${errorCode.code})"
        }
}

/**
 * Classified error metadata extracted from a login or workspace-sync Throwable.
 */
data class ClassifiedLoginError(
    val errorCode: LoginErrorCode,
    val httpStatusCode: Int? = null,
    val backendErrorCode: String? = null,
    val sanitizedMessage: String
)

/**
 * Single immutable diagnostic log entry recorded for an authentication step.
 * Never stores raw passwords, JWTs, or unmasked credentials.
 */
data class LoginDiagnosticEntry(
    val timestampEpochMillis: Long,
    val attemptId: String,
    val stateName: String,
    val targetRole: UserRole,
    val executionMode: AuthExecutionMode,
    val errorCode: LoginErrorCode? = null,
    val httpStatusCode: Int? = null,
    val backendErrorCode: String? = null,
    val detail: String
)

/**
 * Pluggable log sink so production writes to Android Logcat while JVM unit tests
 * can capture output deterministically without relying on android.util.Log.
 */
fun interface DiagnosticLogSink {
    fun write(level: Int, tag: String, message: String)
}

/**
 * Diagnostic logging helper for the PharmaOS login module.
 *
 * Captures [AuthenticationState] transitions, classified [LoginErrorCode] values,
 * HTTP/backend error codes, and post-login workspace authorization diagnostics
 * (especially for the Stockist role) while redacting sensitive identifiers and
 * never logging passwords or JWT tokens.
 */
class LoginDiagnosticLogger(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val logSink: DiagnosticLogSink = AndroidLogcatSink
) {

    private val _authState = MutableStateFlow<AuthenticationState>(AuthenticationState.Idle)
    val authState: StateFlow<AuthenticationState> = _authState.asStateFlow()

    private val _entries = MutableStateFlow<List<LoginDiagnosticEntry>>(emptyList())
    val entries: StateFlow<List<LoginDiagnosticEntry>> = _entries.asStateFlow()

    @Volatile
    private var activeAttemptId: String = generateAttemptId(UserRole.STOCKIST)

    @Volatile
    private var activeExecutionMode: AuthExecutionMode = AuthExecutionMode.PRODUCTION_BACKEND_HTTP

    fun startAttempt(
        targetRole: UserRole,
        executionMode: AuthExecutionMode,
        rawIdentifier: String
    ): String {
        val attemptId = generateAttemptId(targetRole)
        activeAttemptId = attemptId
        activeExecutionMode = executionMode
        val masked = maskIdentifier(rawIdentifier)
        val state = AuthenticationState.ValidatingInput(
            targetRole = targetRole,
            executionMode = executionMode,
            maskedIdentifier = masked
        )
        _authState.value = state
        record(
            attemptId = attemptId,
            level = Log.DEBUG,
            stateName = state.shortLabel,
            targetRole = targetRole,
            executionMode = executionMode,
            detail = "Started ${targetRole.name} login validation for identifier='$masked' in mode=${executionMode.name}"
        )
        return attemptId
    }

    fun recordAuthenticating(
        attemptId: String = activeAttemptId,
        targetRole: UserRole,
        executionMode: AuthExecutionMode,
        method: LoginMethod,
        rawIdentifier: String,
        isBackendConfigured: Boolean,
        backendHostSummary: String
    ) {
        activeAttemptId = attemptId
        activeExecutionMode = executionMode
        val masked = maskIdentifier(rawIdentifier)
        val state = AuthenticationState.Authenticating(
            targetRole = targetRole,
            executionMode = executionMode,
            method = method,
            maskedIdentifier = masked,
            isBackendConfigured = isBackendConfigured
        )
        _authState.value = state
        record(
            attemptId = attemptId,
            level = Log.INFO,
            stateName = state.shortLabel,
            targetRole = targetRole,
            executionMode = executionMode,
            detail = "Authenticating via ${method.name} as ${targetRole.name} (identifier='$masked', backendConfigured=$isBackendConfigured, host=$backendHostSummary)"
        )
    }

    fun recordCredentialsVerified(
        attemptId: String = activeAttemptId,
        targetRole: UserRole,
        executionMode: AuthExecutionMode,
        user: AuthUser
    ) {
        activeAttemptId = attemptId
        activeExecutionMode = executionMode
        val authorized = user.authorizedWorkspaces()
        val state = AuthenticationState.Authenticated(
            targetRole = targetRole,
            actualRole = user.role,
            userId = user.id,
            organizationId = user.organizationId,
            organizationType = user.organizationType,
            permissionStatus = user.permissionStatus,
            authorizedWorkspaces = authorized
        )
        _authState.value = state
        record(
            attemptId = attemptId,
            level = Log.INFO,
            stateName = state.shortLabel,
            targetRole = targetRole,
            executionMode = executionMode,
            detail = "Credentials verified: userId=${user.id}, role=${user.role.name}, status=${user.status.name}, orgId=${user.organizationId}, orgType=${user.organizationType.name}, permStatus=${user.permissionStatus.name}, permCount=${user.permissions.size}, workspaces=${authorized.map { it.name }}"
        )
    }

    fun recordWorkspaceSyncStarted(
        user: AuthUser?,
        executionMode: AuthExecutionMode = activeExecutionMode
    ) {
        val role = user?.role ?: UserRole.STOCKIST
        val state = AuthenticationState.SyncingWorkspaceContext(
            targetRole = role,
            userId = user?.id ?: 0L,
            organizationId = user?.organizationId ?: 0L,
            permissionStatus = user?.permissionStatus ?: PermissionResolutionStatus.MISSING
        )
        _authState.value = state
        record(
            attemptId = activeAttemptId,
            level = Log.DEBUG,
            stateName = state.shortLabel,
            targetRole = role,
            executionMode = executionMode,
            detail = "Syncing active workspace context (userId=${user?.id ?: 0L}, orgId=${user?.organizationId ?: 0L}, permStatus=${user?.permissionStatus?.name ?: "NONE"})"
        )
    }

    fun recordWorkspaceSyncFailure(
        targetRole: UserRole,
        throwable: Throwable,
        executionMode: AuthExecutionMode = activeExecutionMode
    ) {
        val classified = classifyError(throwable, fallbackCode = LoginErrorCode.WORKSPACE_SYNC_FAILED)
        record(
            attemptId = activeAttemptId,
            level = Log.WARN,
            stateName = "WORKSPACE_SYNC_WARNING",
            targetRole = targetRole,
            executionMode = executionMode,
            errorCode = classified.errorCode,
            httpStatusCode = classified.httpStatusCode,
            backendErrorCode = classified.backendErrorCode,
            detail = "Workspace sync error [${classified.errorCode.code}]: ${classified.sanitizedMessage}"
        )
    }

    fun recordWorkspaceRouteResolved(
        user: AuthUser?,
        routedDestination: String,
        executionMode: AuthExecutionMode = activeExecutionMode
    ) {
        val role = user?.role ?: UserRole.STOCKIST
        val authorized = user?.authorizedWorkspaces().orEmpty()
        if (user == null || authorized.isEmpty()) {
            val failureReason = buildWorkspaceDenialReason(user)
            val failedState = AuthenticationState.Failed(
                targetRole = role,
                executionMode = executionMode,
                errorCode = LoginErrorCode.WORKSPACE_AUTHORIZATION_DENIED,
                reason = failureReason
            )
            _authState.value = failedState
            record(
                attemptId = activeAttemptId,
                level = Log.ERROR,
                stateName = failedState.shortLabel,
                targetRole = role,
                executionMode = executionMode,
                errorCode = LoginErrorCode.WORKSPACE_AUTHORIZATION_DENIED,
                detail = "Post-login routing blocked -> $routedDestination. $failureReason"
            )
        } else {
            val state = AuthenticationState.Authenticated(
                targetRole = role,
                actualRole = user.role,
                userId = user.id,
                organizationId = user.organizationId,
                organizationType = user.organizationType,
                permissionStatus = user.permissionStatus,
                authorizedWorkspaces = authorized,
                routedDestination = routedDestination
            )
            _authState.value = state
            record(
                attemptId = activeAttemptId,
                level = Log.INFO,
                stateName = "ROUTED",
                targetRole = role,
                executionMode = executionMode,
                detail = "Routed ${user.role.name} (userId=${user.id}, orgId=${user.organizationId}) to '$routedDestination' (authorized=${authorized.map { it.name }})"
            )
        }
    }

    fun recordValidationFailure(
        attemptId: String = activeAttemptId,
        targetRole: UserRole,
        executionMode: AuthExecutionMode,
        errorCode: LoginErrorCode,
        reason: String
    ) {
        val state = AuthenticationState.Failed(
            targetRole = targetRole,
            executionMode = executionMode,
            errorCode = errorCode,
            reason = reason
        )
        _authState.value = state
        record(
            attemptId = attemptId,
            level = Log.WARN,
            stateName = state.shortLabel,
            targetRole = targetRole,
            executionMode = executionMode,
            errorCode = errorCode,
            detail = "Validation failed [${errorCode.code}]: $reason"
        )
    }

    fun recordFailure(
        attemptId: String = activeAttemptId,
        targetRole: UserRole,
        executionMode: AuthExecutionMode,
        throwable: Throwable
    ): ClassifiedLoginError {
        val classified = classifyError(throwable)
        val state = AuthenticationState.Failed(
            targetRole = targetRole,
            executionMode = executionMode,
            errorCode = classified.errorCode,
            httpStatusCode = classified.httpStatusCode,
            backendErrorCode = classified.backendErrorCode,
            reason = classified.sanitizedMessage
        )
        _authState.value = state
        val httpPart = classified.httpStatusCode?.let { " http=$it" }.orEmpty()
        val backendPart = classified.backendErrorCode?.let { " backendCode=$it" }.orEmpty()
        record(
            attemptId = attemptId,
            level = Log.ERROR,
            stateName = state.shortLabel,
            targetRole = targetRole,
            executionMode = executionMode,
            errorCode = classified.errorCode,
            httpStatusCode = classified.httpStatusCode,
            backendErrorCode = classified.backendErrorCode,
            detail = "Login failed [${classified.errorCode.code}$httpPart$backendPart]: ${classified.sanitizedMessage}"
        )
        return classified
    }

    fun resetState() {
        _authState.value = AuthenticationState.Idle
    }

    fun clear() {
        _entries.value = emptyList()
        _authState.value = AuthenticationState.Idle
    }

    fun formatDiagnosticReport(): String {
        val current = _authState.value
        val snapshot = _entries.value
        val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        val sb = StringBuilder()
        sb.appendLine("PharmaOS Login Diagnostic Trace")
        sb.appendLine("Current AuthenticationState: ${current.shortLabel}")
        if (current is AuthenticationState.Failed) {
            sb.appendLine("ErrorCode: ${current.errorCode.code} (${current.errorCode.name})")
            current.httpStatusCode?.let { sb.appendLine("HTTP Status: $it") }
            current.backendErrorCode?.let { sb.appendLine("Backend Code: $it") }
            sb.appendLine("Reason: ${current.reason}")
        }
        sb.appendLine("Recent Events (${snapshot.size}):")
        snapshot.forEach { entry ->
            val ts = formatter.format(Date(entry.timestampEpochMillis))
            val codeTag = entry.errorCode?.let { " [${it.code}]" }.orEmpty()
            val httpTag = entry.httpStatusCode?.let { " [HTTP $it]" }.orEmpty()
            sb.appendLine("$ts | ${entry.attemptId} | ${entry.targetRole.name} | ${entry.stateName}$codeTag$httpTag | ${entry.detail}")
        }
        return sb.toString().trimEnd()
    }

    private fun record(
        attemptId: String,
        level: Int,
        stateName: String,
        targetRole: UserRole,
        executionMode: AuthExecutionMode,
        errorCode: LoginErrorCode? = null,
        httpStatusCode: Int? = null,
        backendErrorCode: String? = null,
        detail: String
    ) {
        val entry = LoginDiagnosticEntry(
            timestampEpochMillis = clock(),
            attemptId = attemptId,
            stateName = stateName,
            targetRole = targetRole,
            executionMode = executionMode,
            errorCode = errorCode,
            httpStatusCode = httpStatusCode,
            backendErrorCode = backendErrorCode,
            detail = detail
        )
        _entries.update { existing ->
            val updated = existing + entry
            if (updated.size > maxEntries) updated.takeLast(maxEntries) else updated
        }
        val codeSuffix = errorCode?.let { " code=${it.code}" }.orEmpty()
        val httpSuffix = httpStatusCode?.let { " http=$it" }.orEmpty()
        val logLine = "[$attemptId] role=${targetRole.name} mode=${executionMode.name} state=$stateName$codeSuffix$httpSuffix | $detail"
        logSink.write(level, LOG_TAG, logLine)
    }

    private fun buildWorkspaceDenialReason(user: AuthUser?): String {
        if (user == null) {
            return "No active session user found in local store after login."
        }
        val reasons = mutableListOf<String>()
        if (user.status != AccountStatus.ACTIVE) {
            reasons.add("accountStatus=${user.status.name}")
        }
        if (user.permissionStatus != PermissionResolutionStatus.CONFIRMED) {
            reasons.add("permissionStatus=${user.permissionStatus.name}")
        }
        if (user.assignedRoles.isEmpty()) {
            reasons.add("assignedRoles=EMPTY")
        }
        if (user.permissions.isEmpty() && user.role != UserRole.ADMIN) {
            reasons.add("permissions=EMPTY")
        }
        if (user.isProductionContext && user.role != UserRole.ADMIN) {
            if (user.organizationId <= 0L) reasons.add("organizationId=${user.organizationId}")
            if (user.organizationType == OrganizationType.UNKNOWN) reasons.add("organizationType=UNKNOWN")
        }
        if (user.role == UserRole.STOCKIST && user.organizationType != OrganizationType.STOCKIST) {
            reasons.add("stockistOrgTypeMismatch=${user.organizationType.name}")
        }
        val summary = if (reasons.isEmpty()) "noMatchingWorkspaceCapability" else reasons.joinToString(", ")
        return "Workspace authorization failed for role=${user.role.name} (userId=${user.id}): $summary"
    }

    companion object {
        const val LOG_TAG = "PharmaOS.LoginDiag"
        const val DEFAULT_MAX_ENTRIES = 60

        /**
         * Shared diagnostic logger instance used across AuthViewModel and AppNavigation
         * so login credential steps and post-login workspace routing share one unified timeline.
         */
        val shared: LoginDiagnosticLogger by lazy { LoginDiagnosticLogger() }

        fun generateAttemptId(role: UserRole): String {
            val prefix = when (role) {
                UserRole.STOCKIST -> "STK"
                UserRole.MANUFACTURER -> "MFG"
                UserRole.PHARMA_COMPANY -> "CMP"
                UserRole.ADMIN -> "ADM"
            }
            val token = UUID.randomUUID().toString().substring(0, 6).uppercase(Locale.US)
            return "$prefix-$token"
        }

        /**
         * Redacts email/phone identifiers so diagnostic logs help identify lookup issues
         * without leaking full PII or credentials.
         */
        fun maskIdentifier(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isBlank()) return "<blank>"
            val atIndex = trimmed.indexOf('@')
            if (atIndex > 0) {
                val local = trimmed.substring(0, atIndex)
                val domain = trimmed.substring(atIndex + 1)
                val visibleLocal = if (local.length <= 2) local.take(1) else local.take(2)
                return "$visibleLocal***@$domain"
            }
            val digits = trimmed.filter { it.isDigit() }
            if (digits.length >= 4) {
                return "***${digits.takeLast(4)} (len=${trimmed.length})"
            }
            return "${trimmed.take(1)}*** (len=${trimmed.length})"
        }

        /**
         * Maps exceptions thrown during login or workspace sync into a deterministic [ClassifiedLoginError].
         */
        fun classifyError(
            throwable: Throwable,
            fallbackCode: LoginErrorCode = LoginErrorCode.UNKNOWN_ERROR
        ): ClassifiedLoginError {
            val msg = throwable.message?.trim().takeUnless { it.isNullOrBlank() }
                ?: throwable::class.java.simpleName

            if (throwable is SecureTokenStorageException) {
                return ClassifiedLoginError(
                    errorCode = LoginErrorCode.KEYSTORE_STORAGE_ERROR,
                    sanitizedMessage = msg
                )
            }

            if (throwable is ApiException) {
                if (throwable.isNotConfigured) {
                    return ClassifiedLoginError(
                        errorCode = LoginErrorCode.BACKEND_NOT_CONFIGURED,
                        backendErrorCode = throwable.apiError?.errorCode,
                        sanitizedMessage = msg
                    )
                }
                val http = throwable.httpCode
                if (http != null) {
                    val code = when (http) {
                        400 -> LoginErrorCode.HTTP_400_BAD_REQUEST
                        401 -> LoginErrorCode.HTTP_401_UNAUTHORIZED
                        403 -> LoginErrorCode.HTTP_403_FORBIDDEN
                        404 -> LoginErrorCode.HTTP_404_NOT_FOUND
                        422 -> LoginErrorCode.HTTP_422_VALIDATION_FAILED
                        in 500..599 -> LoginErrorCode.HTTP_5XX_SERVER_ERROR
                        else -> LoginErrorCode.HTTP_OTHER_ERROR
                    }
                    return ClassifiedLoginError(
                        errorCode = code,
                        httpStatusCode = http,
                        backendErrorCode = throwable.apiError?.errorCode,
                        sanitizedMessage = msg
                    )
                }
                val netCode = when (throwable.failureType) {
                    NetworkFailureType.TIMEOUT -> LoginErrorCode.NETWORK_TIMEOUT
                    NetworkFailureType.NO_INTERNET -> LoginErrorCode.NETWORK_NO_INTERNET
                    NetworkFailureType.SERVER_UNAVAILABLE -> LoginErrorCode.NETWORK_SERVER_UNAVAILABLE
                    NetworkFailureType.INVALID_JSON -> LoginErrorCode.NETWORK_INVALID_JSON
                    NetworkFailureType.UNEXPECTED, null -> fallbackCode
                }
                return ClassifiedLoginError(
                    errorCode = netCode,
                    backendErrorCode = throwable.apiError?.errorCode,
                    sanitizedMessage = msg
                )
            }

            val lower = msg.lowercase(Locale.US)
            val code = when {
                lower.contains("not configured") -> LoginErrorCode.BACKEND_NOT_CONFIGURED
                lower.contains("registered as") || lower.contains("switch to the") -> LoginErrorCode.ROLE_MISMATCH
                lower.contains("no account registered") -> LoginErrorCode.LOCAL_ACCOUNT_NOT_FOUND
                lower.contains("incorrect password") -> LoginErrorCode.LOCAL_INCORRECT_PASSWORD
                lower.contains("suspended") || lower.contains("status could not be verified") -> LoginErrorCode.ACCOUNT_SUSPENDED_OR_UNVERIFIED
                lower.contains("keystore") -> LoginErrorCode.KEYSTORE_STORAGE_ERROR
                lower.contains("permission") || lower.contains("access denied") -> LoginErrorCode.WORKSPACE_AUTHORIZATION_DENIED
                else -> fallbackCode
            }
            return ClassifiedLoginError(
                errorCode = code,
                sanitizedMessage = msg
            )
        }
    }
}

private object AndroidLogcatSink : DiagnosticLogSink {
    override fun write(level: Int, tag: String, message: String) {
        try {
            when (level) {
                Log.ERROR -> Log.e(tag, message)
                Log.WARN -> Log.w(tag, message)
                Log.INFO -> Log.i(tag, message)
                else -> Log.d(tag, message)
            }
        } catch (_: RuntimeException) {
            // Safe fallback when executed inside plain JVM tests without Android Log stubs
        }
    }
}
