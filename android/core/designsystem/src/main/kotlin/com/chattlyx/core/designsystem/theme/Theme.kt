package com.chattlyx.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** User-selectable appearance mode (Settings > Appearance). */
enum class ChattlyxThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

internal val LocalChattlyxColors = staticCompositionLocalOf { lightChattlyxColors() }

/** ChattlyX roles for the current theme. */
val MaterialTheme.chattlyx: ChattlyxColors
    @Composable
    @ReadOnlyComposable
    get() = LocalChattlyxColors.current

/**
 * Global theme. Brand palette by default; Material You dynamic colour is an
 * explicit user opt-in. AMOLED swaps backgrounds for pure black.
 */
@Composable
fun ChattlyxTheme(
    themeMode: ChattlyxThemeMode = ChattlyxThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ChattlyxThemeMode.SYSTEM -> systemDark
        ChattlyxThemeMode.LIGHT -> false
        ChattlyxThemeMode.DARK, ChattlyxThemeMode.AMOLED -> true
    }
    val amoled = themeMode == ChattlyxThemeMode.AMOLED

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        isDark -> darkScheme(amoled = amoled)
        else -> lightScheme()
    }

    CompositionLocalProvider(
        LocalChattlyxColors provides colorScheme.toChattlyxColors(isDark),
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ChattlyxTypography,
            shapes = ChattlyxShapes,
            content = content,
        )
    }
}
