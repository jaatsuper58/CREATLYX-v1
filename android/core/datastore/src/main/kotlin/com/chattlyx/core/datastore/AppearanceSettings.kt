package com.chattlyx.core.datastore

/** Persisted appearance choices driving the global theme (Section 4.10). */
data class AppearanceSettings(
    val themeMode: com.chattlyx.core.designsystem.theme.ChattlyxThemeMode =
        com.chattlyx.core.designsystem.theme.ChattlyxThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val reduceMotion: Boolean = false,
)
