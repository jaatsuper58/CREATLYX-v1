package com.chattlyx.domain.messaging

import androidx.paging.PagingData
import com.chattlyx.core.common.result.Result
import kotlinx.coroutines.flow.Flow

/** Sending, receipts and sync for 1:1 messages. */
interface MessageRepository {

    /**
     * Encrypts + enqueues an outgoing message; returns the client message id.
     * Delivery state flows through [observeMessages] as receipts arrive.
     */
    suspend fun sendMessage(peerAccountId: String, body: String): Result<String>

    /** MSG-04: acknowledges received envelopes (delete-on-ack server-side). */
    suspend fun acknowledge(serverIds: List<String>)

    /** MSG-09: raises delivered/read state locally and notifies the sender. */
    suspend fun markRead(conversationId: String)

    /** MSG-06: reconciles local state with the server after reconnect. */
    suspend fun sync(): Result<Unit>

    /** Best-effort typing indicator (plaintext routing, metadata only). */
    suspend fun sendTyping(conversationId: String, started: Boolean)
}

/** Chat list + conversation observers. */
interface ConversationRepository {

    fun observeConversations(): Flow<PagingData<Conversation>>

    fun observeConversation(conversationId: String): Flow<Conversation?>

    fun observeMessages(conversationId: String): Flow<PagingData<Message>>

    suspend fun openConversationWith(peerAccountId: String): String

    suspend fun setPinned(conversationId: String, pinned: Boolean)
}

/** Typing indicator surfaced to the conversation UI. */
data class TypingEvent(
    val conversationId: String,
    val senderAccountId: String,
    val started: Boolean,
)

/** Live session events (typing, presence) for the UI layer. */
interface RealtimeEvents {
    val typingEvents: Flow<TypingEvent>
}

/** CON-03 discovery + local contact cache. */
interface ContactRepository {

    fun observeContacts(): Flow<List<ContactInfo>>

    /** Hashes the given E.164 numbers and queries registered peers. */
    suspend fun discover(e164Numbers: List<String>): Result<List<ContactInfo>>

    suspend fun nameFor(accountId: String): String?
}
