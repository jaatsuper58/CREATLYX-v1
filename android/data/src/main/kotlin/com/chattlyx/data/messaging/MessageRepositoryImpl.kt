package com.chattlyx.data.messaging

import com.chattlyx.core.common.dispatchers.ChattlyxDispatcher
import com.chattlyx.core.common.dispatchers.Dispatcher
import com.chattlyx.core.common.error.ChattlyError
import com.chattlyx.core.common.id.UuidV7
import com.chattlyx.core.common.result.Result
import com.chattlyx.core.common.result.fold
import com.chattlyx.core.common.result.getOrElse
import com.chattlyx.core.database.dao.GroupDao
import com.chattlyx.core.database.dao.MessageDao
import com.chattlyx.core.database.entity.MessageEntity
import com.chattlyx.core.database.entity.MessageStatus
import com.chattlyx.core.network.RealtimeClient
import com.chattlyx.core.network.rest.ChattlyxServiceApi
import com.chattlyx.core.network.rest.safeCall
import com.chattlyx.core.protocol.toJavaUuid
import com.chattlyx.core.protocol.toProtoUuid
import com.chattlyx.data.auth.SecureTokenStore
import com.chattlyx.domain.messaging.MessageRepository
import com.chattlyx.domain.messaging.AttachmentKind
import com.chattlyx.domain.messaging.Message
import com.chattlyx.proto.AckFrame
import com.chattlyx.proto.AttachmentContent
import com.chattlyx.proto.Envelope
import com.chattlyx.proto.EnvelopeType
import com.chattlyx.proto.Frame
import com.chattlyx.proto.SessionContent
import com.chattlyx.proto.TextContent
import com.google.protobuf.ByteString
import java.io.File
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber

/** One decrypted message handed to the UI layer. */
data class IncomingMessage(
    val conversationId: String,
    val senderAccountId: String,
    val serverId: String,
    val clientMessageId: String,
    val seq: Long,
    val timestamp: Long,
    val body: String,
    /** Wire name of the attached media ("image"/"video"/"file"/"voice") or null. */
    val attachmentKind: String? = null,
)

/**
 * MSG-01/04/06/09 pipeline. Outbound: plaintext -> placeholder session
 * cipher -> Envelope proto -> realtime socket (+ local persist). Inbound:
 * DeliverFrame -> decrypt -> persist -> ack. Receipts and typing ride the
 * same socket.
 */
@Singleton
class MessageRepositoryImpl @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val api: ChattlyxServiceApi,
    private val realtime: RealtimeClient,
    private val messageDao: MessageDao,
    private val groupDao: GroupDao,
    private val identityKeyStore: IdentityKeyStore,
    private val tokenStore: SecureTokenStore,
    private val peerKeyResolver: PeerKeyResolver,
    private val conversationIds: ClientConversationIds,
    private val attachmentPipeline: AttachmentPipeline,
    @Dispatcher(ChattlyxDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : MessageRepository {

    private val cipher: SessionCipher by lazy { SessionCipher(identityKeyStore) }

    override suspend fun sendMessage(peerAccountId: String, body: String): Result<String> =
        withContext(ioDispatcher) {
            val selfAccountId = tokenStore.accountId() ?: return@withContext Result.failure(ChattlyError.Auth)
            val conversationId = conversationIds.direct(selfAccountId, peerAccountId)
            val clientId = UuidV7.generate().toString()

            val peerKey = peerKeyResolver.publicKeyFor(peerAccountId)
                ?: return@withContext Result.failure(ChattlyError.Crypto.NoSession)

            val content = SessionContent.newBuilder()
                .setText(TextContent.newBuilder().setBody(body))
                .build()
            val ciphertext = try {
                cipher.encrypt(peerKey, content.toByteArray())
            } catch (e: Exception) {
                Timber.w(e, "Encrypt failed for peer")
                return@withContext Result.failure(ChattlyError.Crypto.DecryptFailed)
            }

            val now = System.currentTimeMillis()
            messageDao.insertOrIgnore(
                MessageEntity(
                    id = clientId,
                    clientId = clientId,
                    conversationId = conversationId,
                    senderAccountId = selfAccountId,
                    body = body,
                    status = MessageStatus.PENDING.wire,
                    sentAt = now,
                ),
            )

            val envelope = Envelope.newBuilder()
                .setType(EnvelopeType.ENVELOPE_TYPE_SIGNAL)
                .setSenderAccountId(selfAccountId)
                .setCiphertext(ByteString.copyFrom(ciphertext))
                .setClientMessageId(java.util.UUID.fromString(clientId).toProtoUuid())
                .setConversationId(conversationId)
                .build()

            val sent = realtime.send(
                Frame.newBuilder()
                    .setSend(
                        com.chattlyx.proto.SendFrame.newBuilder()
                            .setEnvelope(envelope)
                            .setRecipientAccountId(peerAccountId),
                    )
                    .build(),
            )

            if (!sent) {
                messageDao.markAcked(clientId, "", MessageStatus.FAILED.wire)
                return@withContext Result.failure(ChattlyError.Network())
            }
            Result.success(clientId)
        }

    override suspend fun sendGroupMessage(groupId: String, body: String): Result<String> =
        withContext(ioDispatcher) {
            val selfAccountId = tokenStore.accountId()
                ?: return@withContext Result.failure(ChattlyError.Auth)
            val conversationId = conversationIds.group(groupId)
            val clientId = UuidV7.generate().toString()

            val members = groupDao.membersOf(groupId)
                .map { it.accountId }
                .filter { it != selfAccountId }
            if (members.isEmpty()) {
                return@withContext Result.failure(
                    ChattlyError.Validation("group", "error_group_no_members"),
                )
            }

            val content = SessionContent.newBuilder()
                .setText(TextContent.newBuilder().setBody(body))
                .build()

            val now = System.currentTimeMillis()
            messageDao.insertOrIgnore(
                MessageEntity(
                    id = clientId,
                    clientId = clientId,
                    conversationId = conversationId,
                    senderAccountId = selfAccountId,
                    body = body,
                    status = MessageStatus.PENDING.wire,
                    sentAt = now,
                ),
            )

            // Fan-out: one pairwise-encrypted envelope per member (placeholder
            // cipher; Sender Keys land in Phase 7). Missing sessions skip that
            // member rather than failing the whole send.
            var anySent = false
            members.forEach { member ->
                val peerKey = peerKeyResolver.publicKeyFor(member) ?: return@forEach
                val ciphertext = try {
                    cipher.encrypt(peerKey, content.toByteArray())
                } catch (e: Exception) {
                    Timber.w(e, "Group fan-out encrypt failed for one member")
                    return@forEach
                }
                val envelope = Envelope.newBuilder()
                    .setType(EnvelopeType.ENVELOPE_TYPE_SIGNAL)
                    .setSenderAccountId(selfAccountId)
                    .setCiphertext(ByteString.copyFrom(ciphertext))
                    .setClientMessageId(java.util.UUID.fromString(clientId).toProtoUuid())
                    .setConversationId(conversationId)
                    .build()
                val sent = realtime.send(
                    Frame.newBuilder()
                        .setSend(
                            com.chattlyx.proto.SendFrame.newBuilder()
                                .setEnvelope(envelope)
                                .setRecipientAccountId(member),
                        )
                        .build(),
                )
                if (sent) anySent = true
            }

            if (!anySent) {
                messageDao.markAcked(clientId, "", MessageStatus.FAILED.wire)
                return@withContext Result.failure(ChattlyError.Network())
            }
            Result.success(clientId)
        }

    /**
     * MED + GRP: group media send. Uploads the ciphertext blob once against
     * the group conversation (server enforces live-membership ACL), then
     * fans out the attachment descriptor per member like [sendGroupMessage].
     */
    override suspend fun sendGroupAttachment(
        groupId: String,
        plaintextFile: File,
        kind: AttachmentKind,
        mimeType: String,
        fileName: String?,
        width: Int?,
        height: Int?,
        durationMs: Int?,
        caption: String,
    ): Result<String> = withContext(ioDispatcher) {
        val selfAccountId = tokenStore.accountId()
            ?: return@withContext Result.failure(ChattlyError.Auth)
        val conversationId = conversationIds.group(groupId)
        val clientId = UuidV7.generate().toString()

        val members = groupDao.membersOf(groupId)
            .map { it.accountId }
            .filter { it != selfAccountId }
        if (members.isEmpty()) {
            return@withContext Result.failure(
                ChattlyError.Validation("group", "error_group_no_members"),
            )
        }

        // Stage a durable copy: the caller's file may be a picker temp file.
        val stagedDir = File(context.filesDir, "attachments").apply { mkdirs() }
        val staged = File(stagedDir, "att-local-$clientId")
        try {
            plaintextFile.inputStream().use { input ->
                staged.outputStream().use { input.copyTo(it) }
            }
        } catch (e: java.io.IOException) {
            Timber.w(e, "Group attachment staging failed")
            return@withContext Result.failure(ChattlyError.Storage.Io(e))
        }

        // One blob for the whole group; declare targets the conversation so
        // every member resolves access server-side (no per-recipient grant).
        val uploaded = attachmentPipeline.upload(
            plaintext = staged,
            kind = kind,
            mimeType = mimeType,
            recipientAccountId = null,
            conversationId = conversationId,
            width = width,
            height = height,
            durationMs = durationMs,
            fileName = fileName,
        ).getOrElse {
            if (!staged.delete()) staged.deleteOnExit()
            return@withContext Result.failure(it)
        }

        val content = SessionContent.newBuilder()
            .setAttachment(
                AttachmentContent.newBuilder()
                    .setKind(kind.toProtoKind())
                    .setAttachmentId(uploaded.attachmentId)
                    .setMimeType(mimeType)
                    .setSizeBytes(uploaded.plaintextSizeBytes)
                    .setSha256(ByteString.copyFrom(uploaded.ciphertextSha256))
                    .apply { width?.takeIf { it > 0 }?.let(::setWidth) }
                    .apply { height?.takeIf { it > 0 }?.let(::setHeight) }
                    .apply { durationMs?.takeIf { it > 0 }?.let(::setDurationMs) }
                    .apply { fileName?.takeIf { it.isNotEmpty() }?.let(::setFileName) }
                    .setKey(ByteString.copyFrom(uploaded.key))
                    .setNonce(ByteString.copyFrom(uploaded.nonce))
                    .build(),
            )
            .build()

        val now = System.currentTimeMillis()
        messageDao.insertOrIgnore(
            MessageEntity(
                id = clientId,
                clientId = clientId,
                conversationId = conversationId,
                senderAccountId = selfAccountId,
                body = caption,
                status = MessageStatus.PENDING.wire,
                sentAt = now,
                attachmentKind = kind.toWireName(),
                attachmentId = uploaded.attachmentId,
                attachmentMime = mimeType,
                attachmentSize = uploaded.plaintextSizeBytes,
                attachmentSha256 = uploaded.ciphertextSha256.toHex(),
                attachmentWidth = width,
                attachmentHeight = height,
                attachmentDurationMs = durationMs,
                attachmentFileName = fileName,
                attachmentKey = Base64.getEncoder().encodeToString(uploaded.key),
                attachmentNonce = Base64.getEncoder().encodeToString(uploaded.nonce),
                attachmentState = STATE_READY,
                attachmentLocalPath = staged.absolutePath,
            ),
        )

        // Fan-out: one pairwise-encrypted envelope per member (placeholder
        // cipher; Sender Keys land in Phase 7). Missing sessions skip that
        // member rather than failing the whole send.
        var anySent = false
        members.forEach { member ->
            val peerKey = peerKeyResolver.publicKeyFor(member) ?: return@forEach
            val ciphertext = try {
                cipher.encrypt(peerKey, content.toByteArray())
            } catch (e: Exception) {
                Timber.w(e, "Group attachment encrypt failed for one member")
                return@forEach
            }
            val envelope = Envelope.newBuilder()
                .setType(EnvelopeType.ENVELOPE_TYPE_SIGNAL)
                .setSenderAccountId(selfAccountId)
                .setCiphertext(ByteString.copyFrom(ciphertext))
                .setClientMessageId(java.util.UUID.fromString(clientId).toProtoUuid())
                .setConversationId(conversationId)
                .build()
            val sent = realtime.send(
                Frame.newBuilder()
                    .setSend(
                        com.chattlyx.proto.SendFrame.newBuilder()
                            .setEnvelope(envelope)
                            .setRecipientAccountId(member),
                    )
                    .build(),
            )
            if (sent) anySent = true
        }

        if (!anySent) {
            messageDao.markAcked(clientId, "", MessageStatus.FAILED.wire)
            return@withContext Result.failure(ChattlyError.Network())
        }
        Result.success(clientId)
    }

    override suspend fun sendAttachment(
        peerAccountId: String,
        plaintextFile: File,
        kind: AttachmentKind,
        mimeType: String,
        fileName: String?,
        width: Int?,
        height: Int?,
        durationMs: Int?,
        caption: String,
    ): Result<String> = withContext(ioDispatcher) {
        val selfAccountId = tokenStore.accountId() ?: return@withContext Result.failure(ChattlyError.Auth)
        val conversationId = conversationIds.direct(selfAccountId, peerAccountId)
        val clientId = UuidV7.generate().toString()

        // Stage a durable copy: the caller's file may be a picker temp file.
        val stagedDir = File(context.filesDir, "attachments").apply { mkdirs() }
        val staged = File(stagedDir, "att-local-$clientId")
        try {
            plaintextFile.inputStream().use { input -> staged.outputStream().use { input.copyTo(it) } }
        } catch (e: java.io.IOException) {
            Timber.w(e, "Attachment staging failed")
            return@withContext Result.failure(ChattlyError.Storage.Io(e))
        }

        val uploaded = attachmentPipeline.upload(
            plaintext = staged,
            kind = kind,
            mimeType = mimeType,
            recipientAccountId = peerAccountId,
            conversationId = conversationId,
            width = width,
            height = height,
            durationMs = durationMs,
            fileName = fileName,
        ).getOrElse {
            if (!staged.delete()) staged.deleteOnExit()
            return@withContext Result.failure(it)
        }

        val content = SessionContent.newBuilder()
            .setAttachment(
                AttachmentContent.newBuilder()
                    .setKind(kind.toProtoKind())
                    .setAttachmentId(uploaded.attachmentId)
                    .setMimeType(mimeType)
                    .setSizeBytes(uploaded.plaintextSizeBytes)
                    .setSha256(ByteString.copyFrom(uploaded.ciphertextSha256))
                    .apply { width?.takeIf { it > 0 }?.let(::setWidth) }
                    .apply { height?.takeIf { it > 0 }?.let(::setHeight) }
                    .apply { durationMs?.takeIf { it > 0 }?.let(::setDurationMs) }
                    .apply { fileName?.takeIf { it.isNotEmpty() }?.let(::setFileName) }
                    .setKey(ByteString.copyFrom(uploaded.key))
                    .setNonce(ByteString.copyFrom(uploaded.nonce))
                    .build(),
            )
            .build()
        val ciphertext = try {
            val peerKey = peerKeyResolver.publicKeyFor(peerAccountId)
                ?: return@withContext Result.failure(ChattlyError.Crypto.NoSession)
            cipher.encrypt(peerKey, content.toByteArray())
        } catch (e: Exception) {
            Timber.w(e, "Attachment message encrypt failed")
            return@withContext Result.failure(ChattlyError.Crypto.DecryptFailed)
        }

        val now = System.currentTimeMillis()
        messageDao.insertOrIgnore(
            MessageEntity(
                id = clientId,
                clientId = clientId,
                conversationId = conversationId,
                senderAccountId = selfAccountId,
                body = caption,
                status = MessageStatus.PENDING.wire,
                sentAt = now,
                attachmentKind = kind.toWireName(),
                attachmentId = uploaded.attachmentId,
                attachmentMime = mimeType,
                attachmentSize = uploaded.plaintextSizeBytes,
                attachmentSha256 = uploaded.ciphertextSha256.toHex(),
                attachmentWidth = width,
                attachmentHeight = height,
                attachmentDurationMs = durationMs,
                attachmentFileName = fileName,
                attachmentKey = Base64.getEncoder().encodeToString(uploaded.key),
                attachmentNonce = Base64.getEncoder().encodeToString(uploaded.nonce),
                attachmentState = STATE_READY,
                attachmentLocalPath = staged.absolutePath,
            ),
        )

        val envelope = Envelope.newBuilder()
            .setType(EnvelopeType.ENVELOPE_TYPE_SIGNAL)
            .setSenderAccountId(selfAccountId)
            .setCiphertext(ByteString.copyFrom(ciphertext))
            .setClientMessageId(java.util.UUID.fromString(clientId).toProtoUuid())
            .setConversationId(conversationId)
            .build()

        val sent = realtime.send(
            Frame.newBuilder()
                .setSend(
                    com.chattlyx.proto.SendFrame.newBuilder()
                        .setEnvelope(envelope)
                        .setRecipientAccountId(peerAccountId),
                )
                .build(),
        )
        if (!sent) {
            messageDao.markAcked(clientId, "", MessageStatus.FAILED.wire)
            return@withContext Result.failure(ChattlyError.Network())
        }
        Result.success(clientId)
    }

    override suspend fun downloadAttachment(message: Message): Result<File> = withContext(ioDispatcher) {
        val attachment = message.attachment
            ?: return@withContext Result.failure(ChattlyError.Validation("attachment", "error_attachment_missing"))
        val key = attachment.key ?: return@withContext Result.failure(ChattlyError.Crypto.NoSession)
        val nonce = attachment.nonce ?: return@withContext Result.failure(ChattlyError.Crypto.NoSession)

        attachment.localPath?.let { path ->
            val existing = File(path)
            if (existing.isFile) return@withContext Result.success(existing)
        }

        messageDao.updateAttachmentState(message.id, STATE_DOWNLOADING, null)
        val result = attachmentPipeline.download(
            attachmentId = attachment.attachmentId,
            key = key,
            nonce = nonce,
            expectedSha256 = attachment.sha256,
        )
        result.fold(
            onSuccess = { file ->
                messageDao.updateAttachmentState(message.id, STATE_READY, file.absolutePath)
            },
            onFailure = {
                messageDao.updateAttachmentState(message.id, STATE_FAILED, null)
            },
        )
        result
    }

    /** Called by the realtime coordinator for every server AckFrame. */
    suspend fun onServerAck(ack: AckFrame) = withContext(ioDispatcher) {
        val clientId = ack.clientMessageId.toJavaUuid().toString()
        if (ack.serverMessageId.isEmpty()) return@withContext
        messageDao.markAcked(clientId, ack.serverMessageId, MessageStatus.SENT.wire)
    }

    /** Decrypts an incoming SIGNAL envelope; null when it cannot be handled. */
    suspend fun onDeliver(envelope: Envelope): IncomingMessage? = withContext(ioDispatcher) {
        if (envelope.type != EnvelopeType.ENVELOPE_TYPE_SIGNAL) return@withContext null

        val peerKey = peerKeyResolver.publicKeyFor(envelope.senderAccountId) ?: run {
            Timber.w("No identity key for sender; dropping envelope")
            return@withContext null
        }

        val plaintext = try {
            cipher.decrypt(peerKey, envelope.ciphertext.toByteArray())
        } catch (e: Exception) {
            Timber.w(e, "Decrypt failed; envelope dropped")
            return@withContext null
        }

        val content = try {
            SessionContent.parseFrom(plaintext)
        } catch (e: Exception) {
            Timber.w(e, "Unparseable session content")
            return@withContext null
        }
        if (content.hasReceipt()) {
            val status = when (content.receipt.kind) {
                com.chattlyx.proto.ReceiptContent.Kind.KIND_DELIVERED -> MessageStatus.DELIVERED.wire
                com.chattlyx.proto.ReceiptContent.Kind.KIND_READ -> MessageStatus.READ.wire
                else -> return@withContext null
            }
            content.receipt.serverMessageIdsList.forEach { serverId ->
                messageDao.updateStatusByServerId(serverId, status)
            }
            return@withContext null
        }

        if (content.hasTyping()) return@withContext null // handled by coordinator

        if (!content.hasText() && !content.hasAttachment()) return@withContext null

        val now = System.currentTimeMillis()
        val clientId = envelope.clientMessageId.toJavaUuid().toString()
        val attachment = if (content.hasAttachment()) content.attachment else null
        messageDao.insertOrIgnore(
            MessageEntity(
                id = clientId,
                clientId = clientId,
                serverId = envelope.serverMessageId,
                conversationId = envelope.conversationId,
                senderAccountId = envelope.senderAccountId,
                body = if (content.hasText()) content.text.body else "",
                status = MessageStatus.DELIVERED.wire,
                seq = envelope.serverSeq,
                sentAt = envelope.serverTimestampMs,
                receivedAt = now,
                attachmentKind = attachment?.kind?.let(::attachmentKindWireName),
                attachmentId = attachment?.attachmentId,
                attachmentMime = attachment?.mimeType,
                attachmentSize = attachment?.sizeBytes?.takeIf { it > 0 },
                attachmentSha256 = attachment?.sha256?.toByteArray()?.toHex(),
                attachmentWidth = attachment?.width?.takeIf { it > 0 },
                attachmentHeight = attachment?.height?.takeIf { it > 0 },
                attachmentDurationMs = attachment?.durationMs?.takeIf { it > 0 },
                attachmentFileName = attachment?.fileName?.takeIf { it.isNotEmpty() },
                attachmentKey = attachment?.key?.toByteArray()?.let(Base64.getEncoder()::encodeToString),
                attachmentNonce = attachment?.nonce?.toByteArray()?.let(Base64.getEncoder()::encodeToString),
                attachmentState = attachment?.let { STATE_PENDING_DOWNLOAD },
            ),
        )

        IncomingMessage(
            conversationId = envelope.conversationId,
            senderAccountId = envelope.senderAccountId,
            serverId = envelope.serverMessageId,
            clientMessageId = clientId,
            seq = envelope.serverSeq,
            timestamp = envelope.serverTimestampMs,
            body = if (content.hasText()) content.text.body else "",
            attachmentKind = attachment?.kind?.let(::attachmentKindWireName),
        )
    }

    override suspend fun acknowledge(serverIds: List<String>) {
        if (serverIds.isEmpty()) return
        realtime.send(
            Frame.newBuilder()
                .setAck(AckFrame.newBuilder().setServerMessageId(serverIds.first()))
                .build(),
        )
        // One AckFrame per id keeps server delete-on-ack simple.
        serverIds.drop(1).forEach { id ->
            realtime.send(Frame.newBuilder().setAck(AckFrame.newBuilder().setServerMessageId(id)).build())
        }
    }

    override suspend fun markRead(conversationId: String) = withContext(ioDispatcher) {
        val selfAccountId = tokenStore.accountId() ?: return@withContext
        val peerAccountId = conversationIds.peerOf(conversationId, selfAccountId) ?: return@withContext

        val serverIds = messageDao.incomingServerIds(
            conversationId = conversationId,
            belowStatus = MessageStatus.READ.wire,
            notFromSender = selfAccountId,
        )
        messageDao.raiseIncomingStatus(
            conversationId = conversationId,
            status = MessageStatus.READ.wire,
            notFromSender = selfAccountId,
        )
        if (serverIds.isEmpty()) return@withContext

        // Receipts ride INSIDE the E2EE session (server sees no metadata).
        val peerKey = peerKeyResolver.publicKeyFor(peerAccountId) ?: return@withContext
        val content = SessionContent.newBuilder()
            .setReceipt(
                com.chattlyx.proto.ReceiptContent.newBuilder()
                    .setKind(com.chattlyx.proto.ReceiptContent.Kind.KIND_READ)
                    .addAllServerMessageIds(serverIds),
            )
            .build()
        val ciphertext = try {
            cipher.encrypt(peerKey, content.toByteArray())
        } catch (e: Exception) {
            Timber.w(e, "Receipt encrypt failed; skipped")
            return@withContext
        }

        realtime.send(
            Frame.newBuilder()
                .setSend(
                    com.chattlyx.proto.SendFrame.newBuilder()
                        .setEnvelope(
                            Envelope.newBuilder()
                                .setType(EnvelopeType.ENVELOPE_TYPE_SIGNAL)
                                .setSenderAccountId(selfAccountId)
                                .setCiphertext(ByteString.copyFrom(ciphertext))
                                .setClientMessageId(UuidV7.generate().toProtoUuid())
                                .setConversationId(conversationId),
                        )
                        .setRecipientAccountId(peerAccountId),
                )
                .build(),
        )
    }

    /**
     * MSG-06: pulls missed envelopes for the given conversations and decrypts
     * them exactly like live deliveries.
     */
    suspend fun syncConversations(conversationCursors: Map<String, Long>): Result<List<IncomingMessage>> {
        val restored = mutableListOf<IncomingMessage>()
        for ((conversationId, afterSeq) in conversationCursors) {
            val result = safeCall { api.history(conversationId, afterSeq, HISTORY_PAGE) }
            if (result !is Result.Success) continue

            for (dto in result.value.envelopes) {
                val envelope = try {
                    Envelope.parseFrom(Base64.getDecoder().decode(dto.envelopeB64))
                } catch (e: Exception) {
                    Timber.w(e, "Bad history envelope skipped")
                    continue
                }
                onDeliver(envelope)?.let { restored += it }
                if (envelope.serverMessageId.isNotEmpty()) {
                    acknowledge(listOf(envelope.serverMessageId))
                }
            }
        }
        return Result.success(restored)
    }

    override suspend fun sendTyping(conversationId: String, started: Boolean) {
        realtime.send(
            Frame.newBuilder()
                .setTyping(
                    com.chattlyx.proto.TypingFrame.newBuilder()
                        .setStarted(started)
                        .setConversationId(conversationId),
                )
                .build(),
        )
    }

    override suspend fun sync(): Result<Unit> = Result.success(Unit)

    companion object {
        const val HISTORY_PAGE = 100
    }
}

/** Resolves a peer's public identity key (key bundle cache, AUTH-06). */
interface PeerKeyResolver {
    suspend fun publicKeyFor(accountId: String): String?
}

/** Client-side canonical conversation ids (matches server rule). */
class ClientConversationIds @Inject constructor() {

    fun direct(a: String, b: String): String {
        val (first, second) = if (a <= b) a to b else b to a
        return "dm:$first:$second"
    }

    /** GRP-*: canonical group conversation id. */
    fun group(groupId: String): String = "grp:$groupId"

    fun isGroup(conversationId: String): Boolean = conversationId.startsWith("grp:")

    fun peerOf(conversationId: String, self: String): String? {
        val parts = conversationId.split(":")
        if (parts.size != 3 || parts[0] != "dm") return null
        return when (self) {
            parts[1] -> parts[2]
            parts[2] -> parts[1]
            else -> null
        }
    }
}

// MED-* attachment state literals persisted on message rows.
internal const val STATE_PENDING_DOWNLOAD = "pending_download"
internal const val STATE_DOWNLOADING = "downloading"
internal const val STATE_READY = "ready"
internal const val STATE_FAILED = "failed"

internal fun AttachmentKind.toProtoKind(): AttachmentContent.Kind = when (this) {
    AttachmentKind.IMAGE -> AttachmentContent.Kind.KIND_IMAGE
    AttachmentKind.VIDEO -> AttachmentContent.Kind.KIND_VIDEO
    AttachmentKind.FILE -> AttachmentContent.Kind.KIND_FILE
    AttachmentKind.VOICE -> AttachmentContent.Kind.KIND_VOICE
}

internal fun attachmentKindWireName(kind: AttachmentContent.Kind): String? = when (kind) {
    AttachmentContent.Kind.KIND_IMAGE -> "image"
    AttachmentContent.Kind.KIND_VIDEO -> "video"
    AttachmentContent.Kind.KIND_FILE -> "file"
    AttachmentContent.Kind.KIND_VOICE -> "voice"
    else -> null
}

internal fun AttachmentKind.toWireName(): String = when (this) {
    AttachmentKind.IMAGE -> "image"
    AttachmentKind.VIDEO -> "video"
    AttachmentKind.FILE -> "file"
    AttachmentKind.VOICE -> "voice"
}

internal fun ByteArray.toHex(): String =
    joinToString("") { "%02x".format(java.util.Locale.ROOT, it) }
