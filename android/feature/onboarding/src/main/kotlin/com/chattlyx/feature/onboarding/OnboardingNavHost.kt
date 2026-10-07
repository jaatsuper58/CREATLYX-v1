package com.chattlyx.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute

/**
 * Onboarding graph (AUTH-01). The app hosts this as its start destination
 * until registration completes, then swaps to the main shell.
 */
@Composable
fun OnboardingNavHost(
    navController: NavHostController,
    onFinishOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = WelcomeRoute,
        modifier = modifier,
    ) {
        composable<WelcomeRoute> {
            WelcomeScreen(
                onGetStarted = { navController.navigate(PhoneEntryRoute) },
            )
        }

        composable<PhoneEntryRoute> {
            PhoneEntryScreen(
                onNumberSubmitted = { e164 -> navController.navigate(OtpRoute(e164)) },
            )
        }

        composable<OtpRoute> { backStackEntry ->
            val e164 = backStackEntry.toRoute<OtpRoute>().e164
            OtpScreen(
                e164 = e164,
                onVerified = {
                    navController.navigate(ProfileSetupRoute) {
                        popUpTo(WelcomeRoute) { inclusive = true }
                    }
                },
            )
        }

        composable<ProfileSetupRoute> {
            ProfileSetupScreen(onFinished = onFinishOnboarding)
        }
    }
}
