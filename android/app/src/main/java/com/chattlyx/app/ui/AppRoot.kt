package com.chattlyx.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chattlyx.app.navigation.ChattlyxShell
import com.chattlyx.core.datastore.AppearanceSettings
import com.chattlyx.core.designsystem.theme.ChattlyxTheme

/**
 * Composition root: applies the user's appearance settings (theme mode, AMOLED,
 * dynamic colour, reduce motion) around the navigation shell.
 */
@Composable
fun ChattlyxAppRoot(
    viewModel: AppViewModel = hiltViewModel(),
) {
    val appearance by viewModel.appearance
        .collectAsStateWithLifecycle(initialValue = AppearanceSettings())

    ChattlyxTheme(
        themeMode = appearance.themeMode,
        dynamicColor = appearance.dynamicColor,
        reduceMotion = appearance.reduceMotion,
    ) {
        ChattlyxShell()
    }
}
