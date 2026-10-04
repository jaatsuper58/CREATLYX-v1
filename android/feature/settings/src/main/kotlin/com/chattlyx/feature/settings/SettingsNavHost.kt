package com.chattlyx.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.chattlyx.feature.settings.account.AccountScreen
import com.chattlyx.feature.settings.danger.DeleteAccountScreen
import com.chattlyx.feature.settings.devices.DevicesScreen

/**
 * Settings entry point (Section 4.10). Hosts the nested graph and reports
 * session-ending events (sign out S36, deletion S50) to the app gate.
 */
@Composable
fun SettingsScreen(
    navController: NavHostController,
    onSessionEnded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = SettingsHomeRoute,
        modifier = modifier,
    ) {
        composable<SettingsHomeRoute> {
            SettingsHomeScreen(
                onOpenAccount = { navController.navigate(AccountRoute) },
                onOpenDevices = { navController.navigate(DevicesRoute) },
                onOpenDeleteAccount = { navController.navigate(DeleteAccountRoute) },
            )
        }

        composable<AccountRoute> {
            AccountScreen(onSignedOut = onSessionEnded)
        }

        composable<DevicesRoute> {
            DevicesScreen()
        }

        composable<DeleteAccountRoute> {
            DeleteAccountScreen(onAccountDeleted = onSessionEnded)
        }
    }
}
