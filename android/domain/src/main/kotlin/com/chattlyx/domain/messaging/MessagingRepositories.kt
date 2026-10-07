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

    /**
     * GRP-02: sends a text message to a group. With the placeholder cipher
     * (pre-libsignal) the client fans out one pairwise-encrypted envelope per
     * member; Sender Keys replace this in Phase 7. Returns client message id.
     */
    suspend fun sendGroupMessage(groupId: String, body: String): Result<String>

    /** MSG-04: acknowledges received envelopes (delete-on-ack server-side). */
    suspend fun acknowledge(serverIds: List<String>)

    /** MSG-09: raises delivered/read state locally and notifies the sender. */
    suspend fun markRead(conversationId: String)

    /** MSG-06: reconciles local state with the server after reconnect. */
    suspend fun sync(): Result<Unit>

    /** Best-effort typing indicator (plaintext routing, metadata only). */
    suspend fun sendTyping(conversationId: String, started: Boolean)

    /**
     * MED-01/02/03: encrypts [plaintextFile], uploads the ciphertext and sends
     * the attachment message. [onProgress] reports (doneBytes, totalBytes)
     * across encrypt + declare + upload. Returns the client message id.
     */
    suspend fun sendAttachment(
        peerAccountId: String,
        plaintextFile: java.io.File,
        kind: AttachmentKind,
        mimeType: String,
        fileName: String? = null,
        width: Int? = null,
        height: Int? = null,
        durationMs: Int? = null,
        caption: String = "",
        onProgress: (doneBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): Result<String>

    /**
     * MED + GRP: group media send. The ciphertext blob is uploaded ONCE,
     * declared against the group conversation (server ACL = live membership),
     * then the attachment descriptor rides one fan-out envelope per member.
     * [onProgress] mirrors [sendAttachment]. Returns the client message id.
     */
    suspend fun sendGroupAttachment(
        groupId: String,
        plaintextFile: java.io.File,
        kind: AttachmentKind,
        mimeType: String,
        fileName: String? = null,
        width: Int? = null,
        height: Int? = null,
        durationMs: Int? = null,
        caption: String = "",
        onProgress: (doneBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): Result<String>

    /** MED-04: fetches + decrypts the blob for [message]; updates local state. */
    suspend fun downloadAttachment(message: Message): Result<java.io.File>
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
