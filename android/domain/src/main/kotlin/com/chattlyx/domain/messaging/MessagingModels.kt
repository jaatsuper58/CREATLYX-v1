package com.chattlyx.domain.messaging

/** Phase 2 1:1 messaging models (MSG-*). */

enum class DeliveryStatus { PENDING, SENT, DELIVERED, READ, FAILED }

/** One row of the chat list. */
data class Conversation(
    val id: String,
    val peerAccountId: String,
    val peerName: String,
    val peerAvatarBlobId: String?,
    val lastMessageText: String,
    val lastMessageAt: Long,
    val unreadCount: Int,
    val pinned: Boolean,
)

/** One message bubble (already decrypted for display). */
data class Message(
    val id: String,
    val clientId: String,
    val serverId: String?,
    val conversationId: String,
    val senderAccountId: String,
    val body: String,
    val status: DeliveryStatus,
    val seq: Long,
    val sentAt: Long,
    val receivedAt: Long?,
    /** True when this device's account sent the message. */
    val isMine: Boolean,
)

/** A registered peer discovered through CON-03. */
data class ContactInfo(
    val accountId: String,
    val displayName: String,
    val username: String?,
    val avatarBlobId: String?,
)
