package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import com.example.data.remote.config.ApiConfig
import com.example.data.remote.config.AuthExecutionMode
import com.example.data.remote.interceptor.TokenProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicLong

private val Context.dataStore by preferencesDataStore(name = "pharmaos_session")

/**
 * Manages non-secret UI/session preferences in DataStore and delegates JWT access/refresh
 * tokens exclusively to Android Keystore-backed AES-256-GCM [SecureTokenStorage].
 *
 * Security hardening:
 * - Legacy plaintext `auth_access_token` / `auth_refresh_token` keys are automatically
 *   migrated into [SecureTokenStorage] and removed from DataStore.
 * - Release builds (`!BuildConfig.DEBUG`) strictly lock `AuthExecutionMode` to
 *   [AuthExecutionMode.PRODUCTION_BACKEND_HTTP] and disallow arbitrary cleartext URL overrides.
 */
class DataStoreManager(
    private val context: Context,
    private val secureTokenStorage: SecureTokenStorage = SecureTokenStorage(context),
    private val allowDevModeOverrides: Boolean = BuildConfig.DEBUG
) : TokenProvider {

    companion object {
        private val KEY_LOGGED_IN_USER_ID = longPreferencesKey("logged_in_user_id")
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val KEY_WELCOME_COMPLETED = booleanPreferencesKey("welcome_completed")
        private val KEY_COMPLETED_ONBOARDING_USER_IDS = stringSetPreferencesKey("completed_onboarding_user_ids")
        private val KEY_DARK_THEME = booleanPreferencesKey("is_dark_theme")
        private val KEY_CRITICAL_DEPLETION_THRESHOLD = intPreferencesKey("critical_depletion_threshold")
        private val KEY_AUTO_DEPLETION_NOTIFICATIONS_ENABLED = booleanPreferencesKey("auto_depletion_notifications_enabled")
        // Legacy plaintext keys kept ONLY for automatic migration & scrubbing
        private val LEGACY_KEY_ACCESS_TOKEN = stringPreferencesKey("auth_access_token")
        private val LEGACY_KEY_REFRESH_TOKEN = stringPreferencesKey("auth_refresh_token")
        private val KEY_BACKEND_BASE_URL = stringPreferencesKey("backend_base_url")
        private val KEY_AUTH_EXECUTION_MODE = stringPreferencesKey("auth_execution_mode")
        private val KEY_ACTIVE_WORKSPACE = stringPreferencesKey("active_workspace")
        const val DEFAULT_DEPLETION_THRESHOLD = 10
    }

    private val tokenMutationLock = Any()
    private val sessionGeneration = AtomicLong(1L)

    @Volatile
    private var lastTrackedUserId: Long? = null

    private val _accessTokenFlow = MutableStateFlow<String?>(secureTokenStorage.getAccessToken())
    private val _refreshTokenFlow = MutableStateFlow<String?>(secureTokenStorage.getRefreshToken())

    override fun getAccessToken(): String? = _accessTokenFlow.value ?: secureTokenStorage.getAccessToken()?.also {
        _accessTokenFlow.value = it
    }

    override fun getRefreshToken(): String? = _refreshTokenFlow.value ?: secureTokenStorage.getRefreshToken()?.also {
        _refreshTokenFlow.value = it
    }

    override fun getSessionGeneration(): Long = sessionGeneration.get()

    override fun updateTokens(accessToken: String?, refreshToken: String?) {
        synchronized(tokenMutationLock) {
            secureTokenStorage.saveTokens(accessToken, refreshToken)
            _accessTokenFlow.value = accessToken?.takeIf { it.isNotBlank() }
            if (!refreshToken.isNullOrBlank()) {
                _refreshTokenFlow.value = refreshToken
            } else if (accessToken.isNullOrBlank()) {
                _refreshTokenFlow.value = null
            }
        }
    }

    override fun updateTokensIfSessionMatches(
        expectedGeneration: Long,
        accessToken: String?,
        refreshToken: String?
    ): Boolean = synchronized(tokenMutationLock) {
        if (sessionGeneration.get() != expectedGeneration) {
            return@synchronized false
        }
        secureTokenStorage.saveTokens(accessToken, refreshToken)
        _accessTokenFlow.value = accessToken?.takeIf { it.isNotBlank() }
        if (!refreshToken.isNullOrBlank()) {
            _refreshTokenFlow.value = refreshToken
        } else if (accessToken.isNullOrBlank()) {
            _refreshTokenFlow.value = null
        }
        true
    }

    override fun clearTokens() {
        synchronized(tokenMutationLock) {
            sessionGeneration.incrementAndGet()
            secureTokenStorage.clearTokens()
            _accessTokenFlow.value = null
            _refreshTokenFlow.value = null
        }
    }

    /**
     * Migrates any legacy plaintext JWT tokens out of DataStore into [SecureTokenStorage]
     * and scrubs them permanently from DataStore.
     */
    suspend fun migrateLegacyPlaintextTokensIfNeeded() {
        context.dataStore.edit { preferences ->
            val legacyAccess = preferences[LEGACY_KEY_ACCESS_TOKEN]
            val legacyRefresh = preferences[LEGACY_KEY_REFRESH_TOKEN]
            if (!legacyAccess.isNullOrBlank() || !legacyRefresh.isNullOrBlank()) {
                if (secureTokenStorage.getAccessToken().isNullOrBlank() && !legacyAccess.isNullOrBlank()) {
                    updateTokens(legacyAccess, legacyRefresh)
                }
                preferences.remove(LEGACY_KEY_ACCESS_TOKEN)
                preferences.remove(LEGACY_KEY_REFRESH_TOKEN)
            }
        }
    }

    val loggedInUserIdFlow: Flow<Long?> = context.dataStore.data.map { preferences ->
        val userId = preferences[KEY_LOGGED_IN_USER_ID]
        if (userId == null || userId <= 0) null else userId
    }

    val isWelcomeCompletedFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_WELCOME_COMPLETED] ?: preferences[KEY_ONBOARDING_COMPLETED] ?: false
    }

    val completedOnboardingUserIdsFlow: Flow<Set<Long>> = context.dataStore.data.map { preferences ->
        preferences[KEY_COMPLETED_ONBOARDING_USER_IDS]
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
            ?: emptySet()
    }

    /**
     * Backward-compatible onboarding completion flow:
     * - If a user is currently logged in, reflects that specific account's onboarding completion.
     * - If logged out, reflects whether the installation has completed the Welcome/initial setup.
     */
    val isOnboardingCompletedFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        val loggedInId = preferences[KEY_LOGGED_IN_USER_ID]?.takeIf { it > 0L }
        val welcomeDone = preferences[KEY_WELCOME_COMPLETED] ?: preferences[KEY_ONBOARDING_COMPLETED] ?: false
        if (loggedInId != null) {
            val completedIds = preferences[KEY_COMPLETED_ONBOARDING_USER_IDS]
                ?.mapNotNull { it.toLongOrNull() }
                ?.toSet()
                ?: emptySet()
            completedIds.contains(loggedInId)
        } else {
            welcomeDone
        }
    }

    fun hasEncryptedTokenArtifacts(): Boolean = secureTokenStorage.hasEncryptedTokens()

    val accessTokenFlow: Flow<String?> = _accessTokenFlow.asStateFlow()

    val refreshTokenFlow: Flow<String?> = _refreshTokenFlow.asStateFlow()

    val backendBaseUrlFlow: Flow<String> = context.dataStore.data.map { preferences ->
        val saved = preferences[KEY_BACKEND_BASE_URL]
        if (!allowDevModeOverrides) {
            // Release builds enforce build-configured HTTPS backend only
            val defaultUrl = ApiConfig.getDefaultBaseUrl()
            if (saved != null && ApiConfig.isSecureForProduction(saved)) saved else defaultUrl
        } else {
            saved ?: ApiConfig.getDefaultBaseUrl()
        }
    }

    val authExecutionModeFlow: Flow<AuthExecutionMode> = context.dataStore.data.map { preferences ->
        if (!allowDevModeOverrides) {
            AuthExecutionMode.PRODUCTION_BACKEND_HTTP
        } else {
            val raw = preferences[KEY_AUTH_EXECUTION_MODE]
            try {
                if (raw != null) AuthExecutionMode.valueOf(raw) else AuthExecutionMode.PRODUCTION_BACKEND_HTTP
            } catch (_: Exception) {
                AuthExecutionMode.PRODUCTION_BACKEND_HTTP
            }
        }
    }

    val isDarkThemeFlow: Flow<Boolean?> = context.dataStore.data.map { preferences ->
        preferences[KEY_DARK_THEME]
    }

    val criticalDepletionThresholdFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_CRITICAL_DEPLETION_THRESHOLD] ?: DEFAULT_DEPLETION_THRESHOLD
    }

    val autoDepletionNotificationsEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUTO_DEPLETION_NOTIFICATIONS_ENABLED] ?: true
    }

    val activeWorkspaceFlow: Flow<com.example.domain.model.WorkspaceType?> = context.dataStore.data.map { preferences ->
        com.example.domain.model.WorkspaceType.fromString(preferences[KEY_ACTIVE_WORKSPACE])
    }

    suspend fun setLoggedInUserId(userId: Long?) {
        val normalized = userId?.takeIf { it > 0L }
        synchronized(tokenMutationLock) {
            if (lastTrackedUserId != normalized) {
                sessionGeneration.incrementAndGet()
                lastTrackedUserId = normalized
            }
        }
        context.dataStore.edit { preferences ->
            if (normalized != null) {
                preferences[KEY_LOGGED_IN_USER_ID] = normalized
            } else {
                preferences.remove(KEY_LOGGED_IN_USER_ID)
            }
        }
    }

    suspend fun setWelcomeCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_WELCOME_COMPLETED] = completed
            preferences[KEY_ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun setAccountOnboardingCompleted(userId: Long, completed: Boolean) {
        if (userId <= 0L) return
        context.dataStore.edit { preferences ->
            val current = preferences[KEY_COMPLETED_ONBOARDING_USER_IDS].orEmpty().toMutableSet()
            if (completed) {
                current.add(userId.toString())
                preferences[KEY_WELCOME_COMPLETED] = true
                preferences[KEY_ONBOARDING_COMPLETED] = true
            } else {
                current.remove(userId.toString())
            }
            preferences[KEY_COMPLETED_ONBOARDING_USER_IDS] = current
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_WELCOME_COMPLETED] = completed
            preferences[KEY_ONBOARDING_COMPLETED] = completed
            val loggedInId = preferences[KEY_LOGGED_IN_USER_ID]?.takeIf { it > 0L }
            if (completed && loggedInId != null) {
                val updated = preferences[KEY_COMPLETED_ONBOARDING_USER_IDS].orEmpty().toMutableSet()
                updated.add(loggedInId.toString())
                preferences[KEY_COMPLETED_ONBOARDING_USER_IDS] = updated
            } else if (!completed) {
                preferences.remove(KEY_COMPLETED_ONBOARDING_USER_IDS)
            }
        }
    }

    suspend fun setAuthTokens(accessToken: String?, refreshToken: String? = null) {
        updateTokens(accessToken, refreshToken)
        // Ensure legacy plaintext keys never exist in DataStore
        context.dataStore.edit { preferences ->
            preferences.remove(LEGACY_KEY_ACCESS_TOKEN)
            preferences.remove(LEGACY_KEY_REFRESH_TOKEN)
        }
    }

    suspend fun clearAuthSession() {
        synchronized(tokenMutationLock) {
            lastTrackedUserId = null
        }
        clearTokens()
        context.dataStore.edit { preferences ->
            preferences.remove(KEY_LOGGED_IN_USER_ID)
            preferences.remove(LEGACY_KEY_ACCESS_TOKEN)
            preferences.remove(LEGACY_KEY_REFRESH_TOKEN)
            preferences.remove(KEY_ACTIVE_WORKSPACE)
            // Preserve KEY_WELCOME_COMPLETED and KEY_COMPLETED_ONBOARDING_USER_IDS on logout
            // so returning verified users skip Welcome & compliance while newly registered accounts
            // still complete their own compliance verification.
        }
    }

    suspend fun setActiveWorkspace(workspace: com.example.domain.model.WorkspaceType?) {
        context.dataStore.edit { preferences ->
            if (workspace != null) {
                preferences[KEY_ACTIVE_WORKSPACE] = workspace.name
            } else {
                preferences.remove(KEY_ACTIVE_WORKSPACE)
            }
        }
    }

    suspend fun setBackendBaseUrl(baseUrl: String) {
        val normalized = ApiConfig.normalizeBaseUrl(baseUrl)
        if (!allowDevModeOverrides && !ApiConfig.isSecureForProduction(normalized)) {
            throw SecurityException("Release builds require a valid HTTPS production backend URL.")
        }
        context.dataStore.edit { preferences ->
            preferences[KEY_BACKEND_BASE_URL] = normalized
        }
    }

    suspend fun setAuthExecutionMode(mode: AuthExecutionMode) {
        if (!allowDevModeOverrides && mode == AuthExecutionMode.DEVELOPMENT_LOCAL_MODE) {
            throw SecurityException("Development Local Mode is disabled in release builds.")
        }
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTH_EXECUTION_MODE] = mode.name
        }
    }

    suspend fun setDarkTheme(isDark: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DARK_THEME] = isDark
        }
    }

    suspend fun setCriticalDepletionThreshold(threshold: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CRITICAL_DEPLETION_THRESHOLD] = threshold.coerceIn(1, 100)
        }
    }

    suspend fun setAutoDepletionNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTO_DEPLETION_NOTIFICATIONS_ENABLED] = enabled
        }
    }
}
