package com.chattlyx.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local message store (Section 7). SQLCipher-encrypted at rest, so message
 * bodies are stored decrypted for FTS — the ciphertext boundary is the wire.
 */

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String, // canonical dm id (server ConversationIds rule)
    @ColumnInfo(name = "peer_account_id") val peerAccountId: String,
    @ColumnInfo(name = "peer_name") val peerName: String = "",
    @ColumnInfo(name = "peer_avatar_blob_id") val peerAvatarBlobId: String? = null,
    @ColumnInfo(name = "last_message_text") val lastMessageText: String = "",
    @ColumnInfo(name = "last_message_at") val lastMessageAt: Long = 0L,
    @ColumnInfo(name = "last_seq") val lastSeq: Long = 0L,
    @ColumnInfo(name = "unread_count") val unreadCount: Int = 0,
    @ColumnInfo(name = "pinned") val pinned: Boolean = false,
    @ColumnInfo(name = "archived") val archived: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = 0L,
)

/** Local send/receive lifecycle (MSG-01). */
enum class MessageStatus(val wire: Int) {
    PENDING(0), // local only, awaiting server ack
    SENT(1), // server acked
    DELIVERED(2), // recipient receipt
    READ(3),
    FAILED(4),
    ;

    companion object {
        fun fromWire(value: Int): MessageStatus = entries.firstOrNull { it.wire == value } ?: PENDING
    }
}

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["conversation_id", "sent_at"]),
        Index(value = ["client_id"], unique = true),
    ],
)
data class MessageEntity(
    @PrimaryKey val id: String, // client UUIDv7; becomes stable identity
    @ColumnInfo(name = "client_id") val clientId: String,
    @ColumnInfo(name = "server_id") val serverId: String? = null,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    @ColumnInfo(name = "sender_account_id") val senderAccountId: String,
    @ColumnInfo(name = "body") val body: String,
    @ColumnInfo(name = "status") val status: Int = MessageStatus.PENDING.wire,
    @ColumnInfo(name = "seq") val seq: Long = 0L,
    @ColumnInfo(name = "sent_at") val sentAt: Long,
    @ColumnInfo(name = "received_at") val receivedAt: Long? = null,
    // Phase 3 (MED-*): attachment descriptor; null for plain text messages.
    @ColumnInfo(name = "attachment_kind") val attachmentKind: String? = null,
    @ColumnInfo(name = "attachment_id") val attachmentId: String? = null,
    @ColumnInfo(name = "attachment_mime") val attachmentMime: String? = null,
    @ColumnInfo(name = "attachment_size") val attachmentSize: Long? = null,
    @ColumnInfo(name = "attachment_sha256") val attachmentSha256: String? = null,
    @ColumnInfo(name = "attachment_width") val attachmentWidth: Int? = null,
    @ColumnInfo(name = "attachment_height") val attachmentHeight: Int? = null,
    @ColumnInfo(name = "attachment_duration_ms") val attachmentDurationMs: Int? = null,
    @ColumnInfo(name = "attachment_file_name") val attachmentFileName: String? = null,
    // Blob key/nonce as base64 — at rest inside the SQLCipher envelope.
    @ColumnInfo(name = "attachment_key") val attachmentKey: String? = null,
    @ColumnInfo(name = "attachment_nonce") val attachmentNonce: String? = null,
    // Local lifecycle: pending_download | downloading | ready | failed.
    @ColumnInfo(name = "attachment_state") val attachmentState: String? = null,
    @ColumnInfo(name = "attachment_local_path") val attachmentLocalPath: String? = null,
)

/** GRP-*: server-known group metadata (membership is server truth). */
@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "created_by") val createdBy: String,
    @ColumnInfo(name = "membership_version") val membershipVersion: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** GRP-*: group membership cache (rebuilt on GroupUpdateFrame). */
@Entity(
    tableName = "group_members",
    primaryKeys = ["group_id", "account_id"],
)
data class GroupMemberEntity(
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "role") val role: String,
    @ColumnInfo(name = "joined_at") val joinedAt: Long,
)

/** Registered peers discovered via CON-03 or first contact. */
@Entity(
    tableName = "contacts",
    indices = [Index(value = ["e164_sha256"], unique = true)],
)
data class ContactEntity(
    @PrimaryKey @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "username") val username: String? = null,
    @ColumnInfo(name = "avatar_blob_id") val avatarBlobId: String? = null,
    @ColumnInfo(name = "e164_sha256") val e164Sha256: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** FTS index over message bodies (SRCH-01 groundwork). */
@Fts4(contentEntity = MessageEntity::class)
@Entity(tableName = "messages_fts")
data class MessageFtsEntity(
    @ColumnInfo(name = "body") val body: String,
)


/** Projection used by the sync engine (MSG-06) to request missed history. */
data class ConversationCursor(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "last_seq") val lastSeq: Long,
)
