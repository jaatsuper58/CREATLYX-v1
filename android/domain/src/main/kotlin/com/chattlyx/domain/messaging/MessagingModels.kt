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

/** Phase 3 (MED-*): what kind of blob a message carries. */
enum class AttachmentKind { IMAGE, VIDEO, FILE, VOICE }

/** Local download lifecycle of an attachment blob. */
enum class AttachmentState { PENDING_DOWNLOAD, DOWNLOADING, READY, FAILED }

/**
 * Attachment descriptor on a message. Key/nonce are only ever populated for
 * messages this device can decrypt (they live inside the E2EE payload).
 */
data class AttachmentInfo(
    val kind: AttachmentKind,
    val attachmentId: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: ByteArray,
    val width: Int?,
    val height: Int?,
    val durationMs: Int?,
    val fileName: String?,
    val key: ByteArray?,
    val nonce: ByteArray?,
    val state: AttachmentState,
    val localPath: String?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AttachmentInfo) return false
        return kind == other.kind && attachmentId == other.attachmentId &&
            mimeType == other.mimeType && sizeBytes == other.sizeBytes &&
            sha256.contentEquals(other.sha256) && width == other.width &&
            height == other.height && durationMs == other.durationMs &&
            fileName == other.fileName && key.contentEquals(other.key) &&
            nonce.contentEquals(other.nonce) && state == other.state &&
            localPath == other.localPath
    }

    override fun hashCode(): Int {
        var result = kind.hashCode()
        result = 31 * result + attachmentId.hashCode()
        result = 31 * result + sha256.contentHashCode()
        result = 31 * result + state.hashCode()
        return result
    }
}

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
    /** Phase 3 (MED-*): non-null when the message carries a blob. */
    val attachment: AttachmentInfo? = null,
)

/** A registered peer discovered through CON-03. */
data class ContactInfo(
    val accountId: String,
    val displayName: String,
    val username: String?,
    val avatarBlobId: String?,
)
