package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.remote.config.ApiConfig
import com.example.data.remote.config.AuthExecutionMode
import com.example.data.remote.interceptor.TokenProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "pharmaos_session")

class DataStoreManager(private val context: Context) : TokenProvider {

    companion object {
        private val KEY_LOGGED_IN_USER_ID = longPreferencesKey("logged_in_user_id")
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val KEY_DARK_THEME = booleanPreferencesKey("is_dark_theme")
        private val KEY_CRITICAL_DEPLETION_THRESHOLD = intPreferencesKey("critical_depletion_threshold")
        private val KEY_AUTO_DEPLETION_NOTIFICATIONS_ENABLED = booleanPreferencesKey("auto_depletion_notifications_enabled")
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("auth_access_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("auth_refresh_token")
        private val KEY_BACKEND_BASE_URL = stringPreferencesKey("backend_base_url")
        private val KEY_AUTH_EXECUTION_MODE = stringPreferencesKey("auth_execution_mode")
        const val DEFAULT_DEPLETION_THRESHOLD = 10
    }

    @Volatile
    private var cachedAccessToken: String? = null

    override fun getAccessToken(): String? = cachedAccessToken

    val loggedInUserIdFlow: Flow<Long?> = context.dataStore.data.map { preferences ->
        val userId = preferences[KEY_LOGGED_IN_USER_ID]
        if (userId == null || userId <= 0) null else userId
    }

    val isOnboardingCompletedFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_ONBOARDING_COMPLETED] ?: false
    }

    val accessTokenFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        val token = preferences[KEY_ACCESS_TOKEN]
        cachedAccessToken = token
        token
    }

    val refreshTokenFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_REFRESH_TOKEN]
    }

    val backendBaseUrlFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_BACKEND_BASE_URL] ?: ApiConfig.getDefaultBaseUrl()
    }

    val authExecutionModeFlow: Flow<AuthExecutionMode> = context.dataStore.data.map { preferences ->
        val raw = preferences[KEY_AUTH_EXECUTION_MODE]
        try {
            if (raw != null) AuthExecutionMode.valueOf(raw) else AuthExecutionMode.PRODUCTION_BACKEND_HTTP
        } catch (_: Exception) {
            AuthExecutionMode.PRODUCTION_BACKEND_HTTP
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

    suspend fun setLoggedInUserId(userId: Long?) {
        context.dataStore.edit { preferences ->
            if (userId != null && userId > 0) {
                preferences[KEY_LOGGED_IN_USER_ID] = userId
            } else {
                preferences.remove(KEY_LOGGED_IN_USER_ID)
            }
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun setAuthTokens(accessToken: String?, refreshToken: String? = null) {
        cachedAccessToken = accessToken
        context.dataStore.edit { preferences ->
            if (!accessToken.isNullOrBlank()) {
                preferences[KEY_ACCESS_TOKEN] = accessToken
            } else {
                preferences.remove(KEY_ACCESS_TOKEN)
            }
            if (!refreshToken.isNullOrBlank()) {
                preferences[KEY_REFRESH_TOKEN] = refreshToken
            } else if (accessToken.isNullOrBlank()) {
                preferences.remove(KEY_REFRESH_TOKEN)
            }
        }
    }

    suspend fun clearAuthSession() {
        cachedAccessToken = null
        context.dataStore.edit { preferences ->
            preferences.remove(KEY_LOGGED_IN_USER_ID)
            preferences.remove(KEY_ACCESS_TOKEN)
            preferences.remove(KEY_REFRESH_TOKEN)
            // Preserve KEY_ONBOARDING_COMPLETED on logout so returning users skip the Welcome Screen
        }
    }

    suspend fun setBackendBaseUrl(baseUrl: String) {
        val normalized = ApiConfig.normalizeBaseUrl(baseUrl)
        context.dataStore.edit { preferences ->
            preferences[KEY_BACKEND_BASE_URL] = normalized
        }
    }

    suspend fun setAuthExecutionMode(mode: AuthExecutionMode) {
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
