package com.chattlyx.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.chattlyx.app.navigation.ChattlyxShell
import com.chattlyx.core.designsystem.theme.ChattlyxTheme
import com.chattlyx.feature.onboarding.OnboardingNavHost

/**
 * Root composable (AUTH-01 gate): splash stays visible while the session is
 * checked, then either the onboarding graph or the authenticated shell.
 */
@Composable
fun ChattlyxAppRoot(
    appViewModel: AppViewModel = hiltViewModel(),
) {
    val gate by appViewModel.gate.collectAsState()
    val appearance by appViewModel.appearance.collectAsState(
        initial = com.chattlyx.core.datastore.AppearanceSettings(),
    )

    ChattlyxTheme(
        themeMode = appearance.themeMode,
        dynamicColor = appearance.dynamicColor,
        reduceMotion = appearance.reduceMotion,
    ) {
        when (gate) {
            RootGate.LOADING -> {
                // SplashScreen keeps showing until the gate resolves.
            }

            RootGate.ONBOARDING -> {
                OnboardingNavHost(
                    navController = rememberNavController(),
                    onFinishOnboarding = appViewModel::completeOnboarding,
                )
            }

            RootGate.MAIN -> {
                ChattlyxShell(onSessionEnded = appViewModel::sessionEnded)
            }
        }
    }
}
