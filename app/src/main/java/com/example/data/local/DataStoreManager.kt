package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "pharmaos_session")

class DataStoreManager(private val context: Context) {

    companion object {
        private val KEY_LOGGED_IN_USER_ID = longPreferencesKey("logged_in_user_id")
        private val KEY_DARK_THEME = booleanPreferencesKey("is_dark_theme")
        private val KEY_CRITICAL_DEPLETION_THRESHOLD = intPreferencesKey("critical_depletion_threshold")
        private val KEY_AUTO_DEPLETION_NOTIFICATIONS_ENABLED = booleanPreferencesKey("auto_depletion_notifications_enabled")
        const val DEFAULT_DEPLETION_THRESHOLD = 10
    }

    val loggedInUserIdFlow: Flow<Long?> = context.dataStore.data.map { preferences ->
        val userId = preferences[KEY_LOGGED_IN_USER_ID]
        if (userId == null || userId <= 0) null else userId
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
