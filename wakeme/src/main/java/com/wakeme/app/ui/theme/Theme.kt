package com.wakeme.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalWakeMePalette = staticCompositionLocalOf { paletteFor(ColorSchemePreset.SIGNAL, false) }
val LocalWakeMeIsDark = staticCompositionLocalOf { false }

private fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) return currentContext
        currentContext = currentContext.baseContext
    }
    return null
}

@Composable
fun WakeMeTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    colorSchemePreset: ColorSchemePreset = ColorSchemePreset.SIGNAL,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themePreference) {
        ThemePreference.SYSTEM -> systemDark
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    val palette = paletteFor(colorSchemePreset, darkTheme)

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.blue,
            secondary = palette.purple,
            tertiary = palette.orange,
            background = palette.bg,
            surface = palette.surface,
            surfaceVariant = palette.bg,
            onPrimary = palette.badgeInk,
            onBackground = palette.ink,
            onSurface = palette.ink,
            onSurfaceVariant = palette.muted,
            outlineVariant = palette.hairline,
            error = palette.red
        )
    } else {
        lightColorScheme(
            primary = palette.blue,
            secondary = palette.purple,
            tertiary = palette.orange,
            background = palette.bg,
            surface = palette.surface,
            surfaceVariant = palette.bg,
            onPrimary = palette.badgeInk,
            onBackground = palette.ink,
            onSurface = palette.ink,
            onSurfaceVariant = palette.muted,
            outlineVariant = palette.hairline,
            error = palette.red
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window
            if (window != null) {
                window.statusBarColor = palette.surface.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalWakeMePalette provides palette,
        LocalWakeMeIsDark provides darkTheme
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
