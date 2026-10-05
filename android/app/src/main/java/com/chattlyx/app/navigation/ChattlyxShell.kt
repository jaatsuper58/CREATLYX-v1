package com.chattlyx.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.chattlyx.core.designsystem.icon.ChattlyxIcons
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

/**
 * App shell: bottom navigation on compact widths, navigation rail on wide
 * screens (WindowSizeClass medium/expanded, tablets and foldables per
 * Section 5.1). Phase 0 threshold: 840 dp.
 */
@Composable
fun ChattlyxShell(onSessionEnded: () -> Unit = {}) {
    val navController = rememberNavController()
    val useRail = LocalConfiguration.current.screenWidthDp >= WIDE_SCREEN_WIDTH_DP

    Box(modifier = Modifier.fillMaxSize()) {
        if (useRail) {
            Row(modifier = Modifier.fillMaxSize()) {
                ShellNavigationRail(navController)
                Scaffold { innerPadding ->
                    ChattlyxNavHost(
                        navController = navController,
                        modifier = Modifier.padding(innerPadding),
                        onSessionEnded = onSessionEnded,
                    )
                }
            }
        } else {
            Scaffold(
                bottomBar = { ShellNavigationBar(navController) },
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    ChattlyxNavHost(
                        navController = navController,
                        onSessionEnded = onSessionEnded,
                    )
                }
            }
        }
        IncomingCallOverlay(navController)
    }
}

/**
 * CALL-02: global ring surface. Shows answer/decline while a call rings and
 * the in-call route is not already on screen; answering navigates there.
 */
@Composable
private fun IncomingCallOverlay(navController: androidx.navigation.NavHostController) {
    val viewModel: com.chattlyx.feature.calls.CallsViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val session by viewModel.sessionState.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val onInCallRoute = backStackEntry?.destination?.hasRoute(com.chattlyx.feature.calls.InCallRoute::class) == true

    val incoming = session as? com.chattlyx.domain.calls.CallSessionState.Incoming ?: return
    if (onInCallRoute) return

    androidx.compose.material3.AlertDialog(
        onDismissRequest = { viewModel.decline() },
        title = { Text(stringResource(com.chattlyx.feature.calls.R.string.call_incoming)) },
        text = { Text(incoming.peerAccountId.take(13)) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                viewModel.accept()
                navController.navigate(com.chattlyx.feature.calls.InCallRoute) {
                    launchSingleTop = true
                }
            }) {
                Text(stringResource(com.chattlyx.feature.calls.R.string.call_answer))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = { viewModel.decline() }) {
                Text(stringResource(com.chattlyx.feature.calls.R.string.call_decline))
            }
        },
    )
}

@Composable
private fun ShellNavigationBar(navController: androidx.navigation.NavHostController) {
    NavigationBar {
        val backStackEntry by navController.currentBackStackEntryAsState()
        TopLevelDestination.entries.forEach { destination ->
            val selected = backStackEntry?.destination?.hasRoute(routeClass(destination)) == true
            NavigationBarItem(
                selected = selected,
                onClick = { navController.navigateToTopLevel(destination) },
                icon = { Icon(destination.icon.imageVector(), contentDescription = null) },
                label = { Text(stringResource(destination.labelRes)) },
            )
        }
    }
}

@Composable
private fun ShellNavigationRail(navController: androidx.navigation.NavHostController) {
    NavigationRail(modifier = Modifier.fillMaxHeight()) {
        val backStackEntry by navController.currentBackStackEntryAsState()
        TopLevelDestination.entries.forEach { destination ->
            val selected = backStackEntry?.destination?.hasRoute(routeClass(destination)) == true
            NavigationRailItem(
                selected = selected,
                onClick = { navController.navigateToTopLevel(destination) },
                icon = { Icon(destination.icon.imageVector(), contentDescription = null) },
                label = { Text(stringResource(destination.labelRes)) },
            )
        }
    }
}

private fun androidx.navigation.NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

/** Maps a destination to its route KClass for hasRoute checks. */
private fun routeClass(destination: TopLevelDestination): kotlin.reflect.KClass<*> =
    destination.route::class

private fun ChattlyxShellIcon.imageVector(): ImageVector = when (this) {
    ChattlyxShellIcon.CHAT -> ChattlyxIcons.Chat
    ChattlyxShellIcon.CALL -> ChattlyxIcons.Call
    ChattlyxShellIcon.CONTACTS -> ChattlyxIcons.Contacts
    ChattlyxShellIcon.SETTINGS -> ChattlyxIcons.Settings
}

private const val WIDE_SCREEN_WIDTH_DP = 840
