package com.chattlyx.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Design tokens from master spec Section 5.5. Every text/background pair must
 * keep WCAG AA contrast (>= 4.5:1); the screenshot suite locks the pairings.
 */

// ---- Brand ----
val PrimaryLight = Color(0xFF4F46E5)
val OnPrimaryLight = Color(0xFFFFFFFF)
val AccentLight = Color(0xFF0891B2)
val BackgroundLight = Color(0xFFFFFFFF)
val SurfaceLight = Color(0xFFF8FAFC)
val BubbleIncomingLight = Color(0xFFF1F5F9)
val BubbleOutgoingStart = Color(0xFF4F46E5)
val BubbleOutgoingEnd = Color(0xFF7C3AED)
val TextPrimaryLight = Color(0xFF0F172A)
val TextSecondaryLight = Color(0xFF475569)
val SuccessLight = Color(0xFF16A34A)
val WarningLight = Color(0xFFD97706)
val ErrorLight = Color(0xFFDC2626)

val PrimaryDark = Color(0xFF8B93FF)
val OnPrimaryDark = Color(0xFF0B0F2E)
val AccentDark = Color(0xFF67E8F9)
val BackgroundDark = Color(0xFF0B1020)
val BackgroundAmoled = Color(0xFF000000)
val SurfaceDark = Color(0xFF111827)
val BubbleIncomingDark = Color(0xFF1F2937)
val TextPrimaryDark = Color(0xFFE5E7EB)
val TextSecondaryDark = Color(0xFF9CA3AF)
val SuccessDark = Color(0xFF4ADE80)
val WarningDark = Color(0xFFFBBF24)
val ErrorDark = Color(0xFFF87171)

/** ChattlyX-specific roles layered on top of Material 3. */
data class ChattlyxColors(
    val bubbleIncoming: Color,
    val bubbleOutgoingStart: Color,
    val bubbleOutgoingEnd: Color,
    val success: Color,
    val warning: Color,
    val readReceipt: Color,
    val textSecondary: Color,
)

internal fun lightChattlyxColors() = ChattlyxColors(
    bubbleIncoming = BubbleIncomingLight,
    bubbleOutgoingStart = BubbleOutgoingStart,
    bubbleOutgoingEnd = BubbleOutgoingEnd,
    success = SuccessLight,
    warning = WarningLight,
    readReceipt = AccentLight,
    textSecondary = TextSecondaryLight,
)

internal fun darkChattlyxColors() = ChattlyxColors(
    bubbleIncoming = BubbleIncomingDark,
    bubbleOutgoingStart = BubbleOutgoingStart,
    bubbleOutgoingEnd = BubbleOutgoingEnd,
    success = SuccessDark,
    warning = WarningDark,
    readReceipt = AccentDark,
    textSecondary = TextSecondaryDark,
)

internal fun lightScheme() = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryLight.copy(alpha = 0.12f),
    onPrimaryContainer = PrimaryLight,
    secondary = AccentLight,
    onSecondary = Color.White,
    tertiary = AccentLight,
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = BubbleIncomingLight,
    onSurfaceVariant = TextSecondaryLight,
    error = ErrorLight,
    onError = Color.White,
    outline = TextSecondaryLight.copy(alpha = 0.4f),
)

internal fun darkScheme(amoled: Boolean = false) = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryDark.copy(alpha = 0.16f),
    onPrimaryContainer = PrimaryDark,
    secondary = AccentDark,
    onSecondary = Color.Black,
    tertiary = AccentDark,
    background = if (amoled) BackgroundAmoled else BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = if (amoled) Color(0xFF05070F) else SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = BubbleIncomingDark,
    onSurfaceVariant = TextSecondaryDark,
    error = ErrorDark,
    onError = Color.Black,
    outline = TextSecondaryDark.copy(alpha = 0.4f),
)

/** Derives the ChattlyX palette that matches a Material scheme. */
internal fun ColorScheme.toChattlyxColors(isDark: Boolean): ChattlyxColors =
    if (isDark) darkChattlyxColors() else lightChattlyxColors()
