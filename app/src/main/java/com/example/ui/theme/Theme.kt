package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = BrandBluePrimaryDark,
    onPrimary = Color(0xFF0F172A),
    primaryContainer = BrandBluePrimary,
    onPrimaryContainer = Color.White,
    secondary = BrandCyanSecondaryDark,
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = BrandCyanSecondary,
    onSecondaryContainer = Color.White,
    tertiary = AmberTertiaryDark,
    onTertiary = Color(0xFF0F172A),
    background = PharmaDarkBackground,
    onBackground = Color(0xFFF8FAFC),
    surface = PharmaDarkSurface,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = PharmaDarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = PharmaDarkOutline,
    error = PharmaError
)

private val LightColorScheme = lightColorScheme(
    primary = BrandBluePrimary,
    onPrimary = BrandBlueOnPrimary,
    primaryContainer = BrandBluePrimaryContainer,
    onPrimaryContainer = BrandBlueOnPrimaryContainer,
    secondary = BrandCyanSecondary,
    onSecondary = BrandCyanOnSecondary,
    secondaryContainer = BrandCyanSecondaryContainer,
    onSecondaryContainer = BrandCyanOnSecondaryContainer,
    tertiary = AmberTertiary,
    onTertiary = AmberOnTertiary,
    tertiaryContainer = AmberTertiaryContainer,
    onTertiaryContainer = AmberOnTertiaryContainer,
    background = PharmaLightBackground,
    onBackground = Color(0xFF0F172A),
    surface = PharmaLightSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = PharmaLightSurfaceVariant,
    onSurfaceVariant = Color(0xFF64748B),
    outline = PharmaLightOutline,
    error = PharmaError
)

@Composable
fun PharmaOSTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
