package com.chattlyx.backend.messaging

import com.chattlyx.backend.common.ChattlyxServerException
import com.chattlyx.backend.common.UuidV7
import com.chattlyx.backend.db.EnvelopeRepository
import com.chattlyx.backend.db.EnvelopeRow
import com.chattlyx.backend.redis.DeliveryQueues
import com.chattlyx.backend.protocol.toJavaUuid
import com.chattlyx.proto.Envelope
import java.util.UUID
import org.slf4j.LoggerFactory

/**
 * 1:1 message pipeline (MSG-01..06): validate, idempotent persist, seq
 * assignment, queue fan-out, then live-connection notify or push wake.
 */
class MessagingService(
    private val envelopes: EnvelopeRepository,
    private val queues: DeliveryQueues,
    private val notifier: PeerNotifier,
    private val push: PushGateway,
    private val tokenLookup: PushTokenLookup,
    private val blockGate: BlockGate = BlockGate.OPEN,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    data class SendResult(val serverMessageId: String, val seq: Long, val serverTimestampMs: Long)

    /** Handles one SendFrame. Idempotent on (sender, client_message_id). */
    fun send(
        senderAccountId: UUID,
        senderDeviceId: Long,
        recipientId: UUID,
        envelope: Envelope,
    ): SendResult {
        if (recipientId == senderAccountId) {
            throw ChattlyxServerException.Validation("cannot send to self")
        }
        if (envelope.ciphertext.isEmpty() || envelope.ciphertext.size() > MAX_CIPHERTEXT_BYTES) {
            throw ChattlyxServerException.Validation("ciphertext missing or oversized")
        }
        if (!envelope.hasClientMessageId()) {
            throw ChattlyxServerException.Validation("client_message_id required")
        }

        val clientMessageId = envelope.clientMessageId.toJavaUuid()

        // Idempotency: a retried send returns the original assignment.
        envelopes.findByClientMessageId(senderAccountId, clientMessageId)?.let { existingId ->
            val existing = envelopes.findById(existingId)
                ?: throw ChattlyxServerException.Internal("idempotent row vanished")
            return SendResult(existing.id, existing.seq, existing.createdAt)
        }

        val conversationId = ConversationIds.direct(senderAccountId, recipientId)
        if (envelope.conversationId.isNotEmpty() && envelope.conversationId != conversationId) {
            throw ChattlyxServerException.Validation("conversation_id does not match participants")
        }

        // SAF-02: messages between blocked peers are silently dropped. The
        // sender still receives a normal ack; nothing is persisted, queued or
        // pushed for the recipient, and the block state is never leaked.
        if (blockGate.blocksEitherWay(senderAccountId, recipientId)) {
            val dropNow = clock()
            return SendResult(
                serverMessageId = UuidV7.generate(dropNow).toString(),
                seq = queues.nextSeq(conversationId),
                serverTimestampMs = dropNow,
            )
        }

        val now = clock()
        val seq = queues.nextSeq(conversationId)
        val envelopeId = UuidV7.generate(now).toString()

        val inserted = envelopes.insert(
            EnvelopeRow(
                id = envelopeId,
                conversationId = conversationId,
                senderAccountId = senderAccountId,
                senderDeviceId = senderDeviceId,
                recipientId = recipientId,
                seq = seq,
                envelopeType = envelope.type.number,
                ciphertext = envelope.ciphertext.toByteArray(),
                clientMessageId = clientMessageId,
                createdAt = now,
                deliveredAt = null,
            ),
        )
        if (!inserted) {
            // Lost the idempotency race: report the winning row.
            val winnerId = envelopes.findByClientMessageId(senderAccountId, clientMessageId)
                ?: throw ChattlyxServerException.Internal("idempotent insert lost")
            val winner = envelopes.findById(winnerId)
                ?: throw ChattlyxServerException.Internal("idempotent row vanished")
            return SendResult(winner.id, winner.seq, winner.createdAt)
        }

        queues.enqueue(recipientId.toString(), envelopeId)

        if (notifier.isLive(recipientId)) {
            notifier.notifyEnvelope(recipientId)
        } else {
            wakeViaPush(recipientId, envelopeId)
        }

        return SendResult(envelopeId, seq, now)
    }

    /** Drains the queue and renders deliverable proto envelopes. */
    fun drainForDelivery(recipientId: UUID, limit: Int = DRAIN_BATCH): List<Envelope> {
        val ids = queues.drain(recipientId.toString(), limit)
        if (ids.isEmpty()) return emptyList()

        val rows = envelopes.findByIds(ids)
            // SAF-02 defence in depth: envelopes queued before a block was
            // placed are filtered out at drain time and never delivered.
            .filterNot { row -> blockGate.blocksEitherWay(row.senderAccountId, recipientId) }
        val now = clock()
        return rows.map { it.toProtoEnvelope() }
    }

    /** MSG-04 delete-on-ack. */
    fun ack(recipientId: UUID, envelopeIds: List<String>) {
        envelopes.ack(envelopeIds, recipientId, clock())
    }

    /** MSG-06: history sync for a conversation past a cursor. */
    fun history(participant: UUID, conversationId: String, afterSeq: Long, limit: Int): List<Envelope> {
        val participants = ConversationIds.participants(conversationId)
            ?: throw ChattlyxServerException.Validation("malformed conversation id")
        if (participant != participants.first && participant != participants.second) {
            throw ChattlyxServerException.Forbidden("not a participant", "msg/not-participant")
        }

        return envelopes.history(participant, conversationId, afterSeq, limit.coerceAtMost(HISTORY_MAX))
            .map { it.toProtoEnvelope() }
    }

    private fun EnvelopeRow.toProtoEnvelope(): Envelope = Envelope.newBuilder()
        .setTypeValue(envelopeType)
        .setSenderAccountId(senderAccountId.toString())
        .setSenderDeviceId(senderDeviceId.toInt())
        .setServerTimestampMs(createdAt)
        .setCiphertext(com.google.protobuf.ByteString.copyFrom(ciphertext))
        .setClientMessageId(
            com.chattlyx.proto.Uuid.newBuilder()
                .setMostSignificantBits(clientMessageId.mostSignificantBits)
                .setLeastSignificantBits(clientMessageId.leastSignificantBits),
        )
        .setConversationId(conversationId)
        .setServerSeq(seq)
        .setServerMessageId(id)
        .build()

    private fun wakeViaPush(recipientId: UUID, envelopeId: String) {
        try {
            tokenLookup.pushTokensFor(recipientId).forEach { (_, token) ->
                push.sendDataOnly(token, envelopeId)
            }
        } catch (e: Exception) {
            logger.warn("Push wake failed; message stays queued", e)
        }
    }

    companion object {
        const val MAX_CIPHERTEXT_BYTES = 256 * 1024
        const val DRAIN_BATCH = 100
        const val HISTORY_MAX = 200

        private val logger = LoggerFactory.getLogger(MessagingService::class.java)
    }
}

/** Live WebSocket presence, implemented by the gateway layer. */
interface PeerNotifier {
    fun isLive(accountId: UUID): Boolean
    fun notifyEnvelope(accountId: UUID)
}

/** NOT-01: data-only push; payload is an opaque envelope id. */
interface PushGateway {
    fun sendDataOnly(fcmToken: String, envelopeId: String)
}

interface PushTokenLookup {
    fun pushTokensFor(accountId: UUID): List<Pair<Long, String>>
}
