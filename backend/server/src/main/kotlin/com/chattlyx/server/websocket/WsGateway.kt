package com.chattlyx.server.websocket

import com.chattlyx.backend.auth.TokenService
import com.chattlyx.backend.common.ChattlyxServerException
import com.chattlyx.backend.messaging.ConversationIds
import com.chattlyx.proto.AckFrame
import com.chattlyx.proto.DeliverFrame
import com.chattlyx.proto.Envelope
import com.chattlyx.proto.ErrorFrame
import com.chattlyx.proto.Frame as ProtoFrame
import com.chattlyx.proto.PongFrame
import com.chattlyx.proto.ReceiptFrame
import com.chattlyx.proto.SendFrame
import com.chattlyx.proto.TypingFrame
import com.chattlyx.server.messaging.MessagingContext
import com.google.protobuf.InvalidProtocolBufferException
import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import java.util.UUID
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("chattlyx.ws")

/**
 * The single multiplexed WebSocket (`wss://…/v1/ws`, master spec 8.3).
 * Binary Protobuf frames: AuthFrame first, then Send/Ack/Ping/Receipt/Typing.
 * Presence TTLs ride Redis; delivery drains the per-account queue.
 */
fun Application.installWsGateway(auth: TokenService, messaging: MessagingContext) {
    messaging.registry.deliverTo = { accountId, session ->
        drainAndDeliver(messaging, accountId, session)
    }

    routing {
        webSocket("/v1/ws") {
            val principal = authenticateFirstFrame(auth) ?: return@webSocket
            val accountId = principal.accountId
            val deviceId = principal.deviceId

            messaging.registry.add(accountId, this)
            messaging.queues.setOnline(accountId.toString(), PRESENCE_TTL_SECONDS)
            logger.info("ws connected account device={}", deviceId)

            try {
                drainAndDeliver(messaging, accountId, this)

                for (frame in incoming) {
                    if (frame !is Frame.Binary) continue
                    val proto = try {
                        ProtoFrame.parseFrom(frame.readBytes())
                    } catch (e: InvalidProtocolBufferException) {
                        sendError("protocol/malformed-frame", "Unparseable frame")
                        continue
                    }

                    try {
                        when {
                            proto.hasSend() -> handleSend(messaging, accountId, deviceId, proto.send)
                            proto.hasAck() -> handleAck(messaging, accountId, proto.ack)
                            proto.hasPing() -> send(
                                Frame.Binary(
                                    true,
                                    ProtoFrame.newBuilder()
                                        .setPong(
                                            PongFrame.newBuilder()
                                                .setClientTimestampMs(proto.ping.clientTimestampMs)
                                                .setServerTimestampMs(System.currentTimeMillis()),
                                        )
                                        .build()
                                        .toByteArray(),
                                ),
                            )

                            proto.hasReceipt() -> routeReceipt(messaging, accountId, proto.receipt)
                            proto.hasTyping() -> routeTyping(messaging, accountId, proto.typing)
                            else -> sendError("protocol/unsupported-frame", "Frame not handled in Phase 2")
                        }
                    } catch (e: ChattlyxServerException) {
                        sendError(e.code, e.message ?: "error")
                    }
                }
            } finally {
                messaging.registry.remove(accountId, this)
                if (messaging.registry.activeSessionCount(accountId) == 0) {
                    messaging.queues.recordOffline(accountId.toString(), System.currentTimeMillis())
                }
                logger.info("ws disconnected account device={}", deviceId)
            }
        }
    }
}

private class WsPrincipal(val accountId: UUID, val deviceId: Long)

/**
 * The very first frame must be an AuthFrame carrying a valid access token.
 * Anything else gets an ErrorFrame and a policy-violation close.
 */
private suspend fun DefaultWebSocketServerSession.authenticateFirstFrame(
    tokenService: TokenService,
): WsPrincipal? {
    val first = try {
        incoming.receive()
    } catch (e: Exception) {
        null
    }

    val bytes = (first as? Frame.Binary)?.readBytes()
    if (bytes == null) {
        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Expected binary AuthFrame"))
        return null
    }

    val frame = try {
        ProtoFrame.parseFrom(bytes)
    } catch (e: InvalidProtocolBufferException) {
        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unparseable AuthFrame"))
        return null
    }

    if (!frame.hasAuth()) {
        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "First frame must be AuthFrame"))
        return null
    }

    val claims = try {
        tokenService.verifyAccess(frame.auth.accessToken)
    } catch (e: ChattlyxServerException) {
        null
    }
    if (claims == null) {
        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "auth/token-invalid"))
        return null
    }

    val accountId = try {
        UUID.fromString(claims.sub)
    } catch (e: IllegalArgumentException) {
        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "auth/token-invalid"))
        return null
    }

    // Device binding: the socket may only act as the device in its token.
    if (frame.auth.deviceId != 0 && frame.auth.deviceId.toLong() != claims.dev) {
        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "auth/device-mismatch"))
        return null
    }

    return WsPrincipal(accountId, claims.dev)
}

private suspend fun DefaultWebSocketServerSession.handleSend(
    messaging: MessagingContext,
    accountId: UUID,
    deviceId: Long,
    sendFrame: SendFrame,
) {
    val recipientId = try {
        UUID.fromString(sendFrame.recipientAccountId)
    } catch (e: IllegalArgumentException) {
        throw ChattlyxServerException.Validation("recipient_account_id must be a UUID")
    }

    val result = messaging.messagingService.send(
        senderAccountId = accountId,
        senderDeviceId = deviceId,
        recipientId = recipientId,
        envelope = sendFrame.envelope,
    )

    // Sender ack: server id + seq + timestamp for the client's send state.
    send(
        Frame.Binary(
            true,
            ProtoFrame.newBuilder()
                .setAck(
                    AckFrame.newBuilder()
                        .setClientMessageId(sendFrame.envelope.clientMessageId)
                        .setServerMessageId(result.serverMessageId)
                        .setServerTimestampMs(result.serverTimestampMs),
                )
                .build()
                .toByteArray(),
        ),
    )
}

private suspend fun DefaultWebSocketServerSession.handleAck(
    messaging: MessagingContext,
    accountId: UUID,
    ack: AckFrame,
) {
    if (ack.serverMessageId.isNotEmpty()) {
        messaging.messagingService.ack(accountId, listOf(ack.serverMessageId))
    }
}

/** Best-effort receipt routing (MSG-09): ephemeral, never persisted. */
private suspend fun DefaultWebSocketServerSession.routeReceipt(
    messaging: MessagingContext,
    accountId: UUID,
    receipt: ReceiptFrame,
) {
    val peer = peerOf(accountId, receipt.conversationId) ?: return
    if (!messaging.registry.isLive(peer)) return

    val envelope = Envelope.newBuilder()
        .setType(com.chattlyx.proto.EnvelopeType.ENVELOPE_TYPE_RECEIPT)
        .setSenderAccountId(accountId.toString())
        .setConversationId(receipt.conversationId)
        .build()
    deliverEnvelope(messaging, peer, envelope)
}

private suspend fun DefaultWebSocketServerSession.routeTyping(
    messaging: MessagingContext,
    accountId: UUID,
    typing: TypingFrame,
) {
    val peer = peerOf(accountId, typing.conversationId) ?: return
    if (!messaging.registry.isLive(peer)) return

    val envelope = Envelope.newBuilder()
        .setType(com.chattlyx.proto.EnvelopeType.ENVELOPE_TYPE_TYPING)
        .setSenderAccountId(accountId.toString())
        .setConversationId(typing.conversationId)
        .setCiphertext(com.google.protobuf.ByteString.copyFrom(byteArrayOf(if (typing.started) 1 else 0)))
        .build()
    deliverEnvelope(messaging, peer, envelope)
}

private suspend fun deliverEnvelope(
    messaging: MessagingContext,
    accountId: UUID,
    envelope: Envelope,
) {
    // Ephemeral delivery via the registry's live sessions (receipts/typing).
    val frame = Frame.Binary(true, ProtoFrame.newBuilder().setDeliver(
        DeliverFrame.newBuilder().setEnvelope(envelope),
    ).build().toByteArray())
    messaging.registry.broadcast(accountId, frame)
}

private suspend fun drainAndDeliver(
    messaging: MessagingContext,
    accountId: UUID,
    session: DefaultWebSocketServerSession,
) {
    messaging.messagingService.drainForDelivery(accountId).forEach { envelope ->
        session.send(
            Frame.Binary(
                true,
                ProtoFrame.newBuilder()
                    .setDeliver(DeliverFrame.newBuilder().setEnvelope(envelope))
                    .build()
                    .toByteArray(),
            ),
        )
    }
}

private suspend fun DefaultWebSocketServerSession.sendError(code: String, message: String) {
    send(
        Frame.Binary(
            true,
            ProtoFrame.newBuilder()
                .setError(ErrorFrame.newBuilder().setCode(code).setMessage(message))
                .build()
                .toByteArray(),
        ),
    )
}

private fun peerOf(self: UUID, conversationId: String): UUID? {
    val participants = ConversationIds.participants(conversationId) ?: return null
    return when (self) {
        participants.first -> participants.second
        participants.second -> participants.first
        else -> null
    }
}

private const val PRESENCE_TTL_SECONDS = 75L
