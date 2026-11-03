package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.PharmaOSTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as PharmaApplication

        setContent {
            val userDarkThemePref by app.dataStoreManager.isDarkThemeFlow.collectAsState(initial = false)
            val isDark = userDarkThemePref ?: false

            PharmaOSTheme(darkTheme = isDark) {
                AppNavigation()
            }
        }
    }
}
