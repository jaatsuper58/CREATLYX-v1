package com.chattlyx.feature.chats

import kotlinx.serialization.Serializable

/** Type-safe route for the chat list (MSG-08). */
@Serializable
data object ChatsRoute

/** Type-safe route for one conversation (MSG-03). */
@Serializable
data class ConversationRoute(val conversationId: String)
