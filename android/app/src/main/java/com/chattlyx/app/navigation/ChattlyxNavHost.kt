package com.chattlyx.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.chattlyx.feature.calls.CallsRoute
import com.chattlyx.feature.calls.CallsScreen
import com.chattlyx.feature.chats.ChatsRoute
import com.chattlyx.feature.chats.ChatsScreen
import com.chattlyx.feature.chats.ConversationRoute
import com.chattlyx.feature.chats.ConversationScreen
import com.chattlyx.feature.contacts.ContactsRoute
import com.chattlyx.feature.contacts.ContactsScreen
import com.chattlyx.feature.settings.SettingsRoute
import com.chattlyx.feature.settings.SettingsScreen

/**
 * Root navigation graph (type-safe routes, master spec Sections 3.1/5.1).
 * Phase 2 adds the conversation route (MSG-03) and wires contacts->chat.
 */
@Composable
fun ChattlyxNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    onSessionEnded: () -> Unit = {},
) {
    NavHost(
        navController = navController,
        startDestination = ChatsRoute,
        modifier = modifier,
    ) {
        composable<ChatsRoute> {
            ChatsScreen(
                onOpenConversation = { conversationId ->
                    navController.navigate(ConversationRoute(conversationId))
                },
                onInvite = {
                    navController.navigate(ContactsRoute) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
        composable<CallsRoute> { CallsScreen() }
        composable<ContactsRoute> {
            ContactsScreen(
                onOpenChat = { conversationId ->
                    navController.navigate(ConversationRoute(conversationId))
                },
            )
        }
        composable<ConversationRoute> {
            ConversationScreen(onBack = { navController.popBackStack() })
        }
        composable<SettingsRoute> {
            val settingsNavController = rememberNavController()
            SettingsScreen(navController = settingsNavController, onSessionEnded = onSessionEnded)
        }
    }
}
