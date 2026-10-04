package com.chattlyx.data.messaging

import com.chattlyx.core.common.dispatchers.ChattlyxDispatcher
import com.chattlyx.core.common.dispatchers.Dispatcher
import com.chattlyx.core.common.error.ChattlyError
import com.chattlyx.core.common.id.UuidV7
import com.chattlyx.core.common.result.Result
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
import com.chattlyx.proto.AckFrame
import com.chattlyx.proto.Envelope
import com.chattlyx.proto.EnvelopeType
import com.chattlyx.proto.Frame
import com.chattlyx.proto.SessionContent
import com.chattlyx.proto.TextContent
import com.google.protobuf.ByteString
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
)

/**
 * MSG-01/04/06/09 pipeline. Outbound: plaintext -> placeholder session
 * cipher -> Envelope proto -> realtime socket (+ local persist). Inbound:
 * DeliverFrame -> decrypt -> persist -> ack. Receipts and typing ride the
 * same socket.
 */
@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val api: ChattlyxServiceApi,
    private val realtime: RealtimeClient,
    private val messageDao: MessageDao,
    private val identityKeyStore: IdentityKeyStore,
    private val tokenStore: SecureTokenStore,
    private val peerKeyResolver: PeerKeyResolver,
    private val conversationIds: ClientConversationIds,
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

        if (!content.hasText()) return@withContext null

        val now = System.currentTimeMillis()
        val clientId = envelope.clientMessageId.toJavaUuid().toString()
        messageDao.insertOrIgnore(
            MessageEntity(
                id = clientId,
                clientId = clientId,
                serverId = envelope.serverMessageId,
                conversationId = envelope.conversationId,
                senderAccountId = envelope.senderAccountId,
                body = content.text.body,
                status = MessageStatus.DELIVERED.wire,
                seq = envelope.serverSeq,
                sentAt = envelope.serverTimestampMs,
                receivedAt = now,
            ),
        )

        IncomingMessage(
            conversationId = envelope.conversationId,
            senderAccountId = envelope.senderAccountId,
            serverId = envelope.serverMessageId,
            clientMessageId = clientId,
            seq = envelope.serverSeq,
            timestamp = envelope.serverTimestampMs,
            body = content.text.body,
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
