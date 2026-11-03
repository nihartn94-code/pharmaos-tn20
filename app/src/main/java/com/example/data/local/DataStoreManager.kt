package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "pharmaos_session")

class DataStoreManager(private val context: Context) {

    companion object {
        private val KEY_LOGGED_IN_USER_ID = longPreferencesKey("logged_in_user_id")
        private val KEY_DARK_THEME = booleanPreferencesKey("is_dark_theme")
    }

    val loggedInUserIdFlow: Flow<Long?> = context.dataStore.data.map { preferences ->
        val userId = preferences[KEY_LOGGED_IN_USER_ID]
        if (userId == null || userId <= 0) null else userId
    }

    val isDarkThemeFlow: Flow<Boolean?> = context.dataStore.data.map { preferences ->
        preferences[KEY_DARK_THEME]
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
}
