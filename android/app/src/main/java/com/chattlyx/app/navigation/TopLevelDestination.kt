package com.chattlyx.app.navigation

import androidx.annotation.StringRes
import com.chattlyx.app.R
import com.chattlyx.feature.calls.CallsRoute
import com.chattlyx.feature.chats.ChatsRoute
import com.chattlyx.feature.contacts.ContactsRoute
import com.chattlyx.feature.settings.SettingsRoute

/**
 * Destinations of the bottom navigation / rail (master spec Section 5.1).
 * Route objects live in their feature modules; this mapping is the only place
 * the app knows about them, so features never depend on each other.
 */
enum class TopLevelDestination(
    val route: Any,
    val icon: ChattlyxShellIcon,
    @StringRes val labelRes: Int,
) {
    CHATS(ChatsRoute, ChattlyxShellIcon.CHAT, R.string.nav_chats),
    CALLS(CallsRoute, ChattlyxShellIcon.CALL, R.string.nav_calls),
    CONTACTS(ContactsRoute, ChattlyxShellIcon.CONTACTS, R.string.nav_contacts),
    SETTINGS(SettingsRoute, ChattlyxShellIcon.SETTINGS, R.string.nav_settings),
}

/** Icon selector kept dependency-free so previews stay cheap. */
enum class ChattlyxShellIcon { CHAT, CALL, CONTACTS, SETTINGS }
