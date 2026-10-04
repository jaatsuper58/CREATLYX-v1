package com.chattlyx.data.messaging

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.chattlyx.core.common.dispatchers.ChattlyxDispatcher
import com.chattlyx.core.common.dispatchers.Dispatcher
import com.chattlyx.core.database.dao.ContactDao
import com.chattlyx.core.database.dao.ConversationDao
import com.chattlyx.core.database.dao.MessageDao
import com.chattlyx.core.database.entity.ConversationEntity
import com.chattlyx.core.database.entity.MessageEntity
import com.chattlyx.data.auth.SecureTokenStore
import com.chattlyx.domain.messaging.Conversation
import com.chattlyx.domain.messaging.ConversationRepository
import com.chattlyx.domain.messaging.DeliveryStatus
import com.chattlyx.domain.messaging.Message
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room-backed chat list and conversation streams (MSG-03/08). */
@Singleton
class ConversationRepositoryImpl @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val contactDao: ContactDao,
    private val tokenStore: SecureTokenStore,
    private val conversationIds: ClientConversationIds,
    @Dispatcher(ChattlyxDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : ConversationRepository {

    override fun observeConversations(): Flow<PagingData<Conversation>> =
        Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
            pagingSourceFactory = { conversationDao.paged() },
        ).flow.map { page -> page.map { it.toDomain() } }

    override fun observeConversation(conversationId: String): Flow<Conversation?> =
        conversationDao.byId(conversationId).map { it?.toDomain() }

    override fun observeMessages(conversationId: String): Flow<PagingData<Message>> =
        Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
            pagingSourceFactory = { messageDao.pagedByConversation(conversationId) },
        ).flow.map { page -> page.map { it.toDomain(selfAccountId) } }

    override suspend fun openConversationWith(peerAccountId: String): String {
        val self = tokenStore.accountId() ?: return ""
        val id = conversationIds.direct(self, peerAccountId)

        if (conversationDao.byPeer(peerAccountId) == null) {
            val peer = contactDao.byAccountId(peerAccountId)
            conversationDao.upsert(
                ConversationEntity(
                    id = id,
                    peerAccountId = peerAccountId,
                    peerName = peer?.displayName ?: peerAccountId.take(8),
                    peerAvatarBlobId = peer?.avatarBlobId,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
        return id
    }

    override suspend fun setPinned(conversationId: String, pinned: Boolean) {
        conversationDao.setPinned(conversationId, pinned)
    }

    /** Applies an incoming message to list state (preview, seq, unread). */
    suspend fun onIncomingMessage(message: IncomingMessage, incrementUnread: Boolean) {
        val conversation = conversationDao.byIdOnce(message.conversationId)
        if (conversation == null) {
            val self = tokenStore.accountId()
            val peer = conversationIds.peerOf(message.conversationId, self.orEmpty()) ?: return
            val contact = contactDao.byAccountId(peer)
            conversationDao.upsert(
                ConversationEntity(
                    id = message.conversationId,
                    peerAccountId = peer,
                    peerName = contact?.displayName ?: peer.take(8),
                    peerAvatarBlobId = contact?.avatarBlobId,
                    lastMessageText = message.body,
                    lastMessageAt = message.timestamp,
                    lastSeq = message.seq,
                    unreadCount = if (incrementUnread) 1 else 0,
                    createdAt = message.timestamp,
                ),
            )
        } else {
            conversationDao.touch(
                id = message.conversationId,
                preview = message.body,
                at = message.timestamp,
                seq = message.seq,
            )
            if (incrementUnread) {
                conversationDao.incrementUnread(message.conversationId)
            }
        }
    }

    private fun ConversationEntity.toDomain() = Conversation(
        id = id,
        peerAccountId = peerAccountId,
        peerName = peerName,
        peerAvatarBlobId = peerAvatarBlobId,
        lastMessageText = lastMessageText,
        lastMessageAt = lastMessageAt,
        unreadCount = unreadCount,
        pinned = pinned,
    )

    private fun MessageEntity.toDomain(selfId: String?) = Message(
        id = id,
        clientId = clientId,
        serverId = serverId,
        conversationId = conversationId,
        senderAccountId = senderAccountId,
        body = body,
        status = when (com.chattlyx.core.database.entity.MessageStatus.fromWire(status)) {
            com.chattlyx.core.database.entity.MessageStatus.PENDING -> DeliveryStatus.PENDING
            com.chattlyx.core.database.entity.MessageStatus.SENT -> DeliveryStatus.SENT
            com.chattlyx.core.database.entity.MessageStatus.DELIVERED -> DeliveryStatus.DELIVERED
            com.chattlyx.core.database.entity.MessageStatus.READ -> DeliveryStatus.READ
            com.chattlyx.core.database.entity.MessageStatus.FAILED -> DeliveryStatus.FAILED
        },
        seq = seq,
        sentAt = sentAt,
        receivedAt = receivedAt,
        isMine = selfId != null && senderAccountId == selfId,
        attachment = toAttachmentInfo(),
    )

    /** MED-*: rebuilds the attachment descriptor from persisted columns. */
    private fun MessageEntity.toAttachmentInfo(): com.chattlyx.domain.messaging.AttachmentInfo? {
        val id = attachmentId ?: return null
        val kind = when (attachmentKind) {
            "image" -> com.chattlyx.domain.messaging.AttachmentKind.IMAGE
            "video" -> com.chattlyx.domain.messaging.AttachmentKind.VIDEO
            "file" -> com.chattlyx.domain.messaging.AttachmentKind.FILE
            "voice" -> com.chattlyx.domain.messaging.AttachmentKind.VOICE
            else -> com.chattlyx.domain.messaging.AttachmentKind.FILE
        }
        return com.chattlyx.domain.messaging.AttachmentInfo(
            kind = kind,
            attachmentId = id,
            mimeType = attachmentMime ?: "application/octet-stream",
            sizeBytes = attachmentSize ?: 0L,
            sha256 = attachmentSha256?.let(::hexToBytes) ?: ByteArray(0),
            width = attachmentWidth,
            height = attachmentHeight,
            durationMs = attachmentDurationMs,
            fileName = attachmentFileName,
            key = attachmentKey?.let { java.util.Base64.getDecoder().decode(it) },
            nonce = attachmentNonce?.let { java.util.Base64.getDecoder().decode(it) },
            state = when (attachmentState) {
                STATE_DOWNLOADING -> com.chattlyx.domain.messaging.AttachmentState.DOWNLOADING
                STATE_READY -> com.chattlyx.domain.messaging.AttachmentState.READY
                STATE_FAILED -> com.chattlyx.domain.messaging.AttachmentState.FAILED
                else -> com.chattlyx.domain.messaging.AttachmentState.PENDING_DOWNLOAD
            },
            localPath = attachmentLocalPath,
        )
    }

    /** Read once; the account id never changes within a session. */
    private val selfAccountId: String? by lazy {
        kotlinx.coroutines.runBlocking { tokenStore.accountId() }
    }

    private companion object {
        const val PAGE_SIZE = 30
    }
}


private fun hexToBytes(hex: String): ByteArray =
    ByteArray(hex.length / 2) { i ->
        hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
