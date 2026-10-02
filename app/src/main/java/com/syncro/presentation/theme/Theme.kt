package com.syncro.presentation.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import android.app.Activity
import androidx.core.view.WindowCompat

/** También los usa el widget (Glance), que no puede leer el tema de Compose. */
internal val DarkColorScheme = darkColorScheme(
    primary = Cyan400,
    secondary = Emerald500,
    tertiary = Indigo500,
    background = Color(0xFF0F172A), // Slate 900
    surface = Color(0xFF1E293B),    // Slate 800
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Slate50,
    onSurface = Slate50,
    onSurfaceVariant = Slate200,
    outline = Slate500.copy(alpha = 0.5f)
)

internal val LightColorScheme = lightColorScheme(
    primary = Cyan800,
    onPrimary = Color.White,
    primaryContainer = Cyan100,
    onPrimaryContainer = Cyan900,
    secondary = Emerald500,
    onSecondary = Color.White,
    secondaryContainer = Emerald50,
    onSecondaryContainer = Emerald800,
    tertiary = Indigo500,
    onTertiary = Color.White,
    tertiaryContainer = Indigo50,
    onTertiaryContainer = Indigo900,
    background = Color(0xFFF1F5F9), // Slate 100 - Un poco más oscuro para contraste
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Color(0xFFE2E8F0), // Slate 200 - Para mayor contraste en campos
    onSurfaceVariant = Slate500,
    outline = Color(0xFFCBD5E1) // Slate 300
)

@Composable
fun SyncroTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is disabled by default to use the custom palette
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

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
