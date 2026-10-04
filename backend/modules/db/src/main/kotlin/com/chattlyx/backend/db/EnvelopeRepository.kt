package com.chattlyx.backend.db

import java.util.UUID
import javax.sql.DataSource

data class EnvelopeRow(
    val id: String,
    val conversationId: String,
    val senderAccountId: UUID,
    val senderDeviceId: Long,
    val recipientId: UUID,
    val seq: Long,
    val envelopeType: Int,
    val ciphertext: ByteArray,
    val clientMessageId: UUID,
    val createdAt: Long,
    val deliveredAt: Long?,
)

/**
 * Durable envelope store (MSG-02). Ciphertext only. Rows die on ack
 * (delete-on-ack); a TTL job purges anything older than 30 days.
 */
class EnvelopeRepository(private val db: DataSource) {

    /** Returns false when the idempotency key already exists (duplicate send). */
    fun insert(row: EnvelopeRow): Boolean {
        db.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO envelopes
                (id, conversation_id, sender_account_id, sender_device_id, recipient_id,
                 seq, envelope_type, ciphertext, client_message_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (sender_account_id, client_message_id) DO NOTHING
                """.trimIndent(),
            ).use { s ->
                s.setString(1, row.id)
                s.setString(2, row.conversationId)
                s.setObject(3, row.senderAccountId)
                s.setLong(4, row.senderDeviceId)
                s.setObject(5, row.recipientId)
                s.setLong(6, row.seq)
                s.setInt(7, row.envelopeType)
                s.setBytes(8, row.ciphertext)
                s.setObject(9, row.clientMessageId)
                s.setLong(10, row.createdAt)
                return s.executeUpdate() == 1
            }
        }
    }

    /** Idempotency lookup: the existing server id for a client message id. */
    fun findByClientMessageId(senderId: UUID, clientMessageId: UUID): String? {
        db.connection.use { connection ->
            connection.prepareStatement(
                "SELECT id FROM envelopes WHERE sender_account_id = ? AND client_message_id = ?",
            ).use { s ->
                s.setObject(1, senderId)
                s.setObject(2, clientMessageId)
                s.executeQuery().use { rs ->
                    return if (rs.next()) rs.getString(1) else null
                }
            }
        }
    }

    fun findById(id: String): EnvelopeRow? {
        db.connection.use { connection ->
            connection.prepareStatement("SELECT * FROM envelopes WHERE id = ?").use { s ->
                s.setString(1, id)
                s.executeQuery().use { rs ->
                    return if (rs.next()) mapRow(rs) else null
                }
            }
        }
    }

    fun findByIds(ids: List<String>): List<EnvelopeRow> {
        if (ids.isEmpty()) return emptyList()
        val placeholders = ids.joinToString(",") { "?" }
        db.connection.use { connection ->
            connection.prepareStatement(
                "SELECT * FROM envelopes WHERE id IN ($placeholders) ORDER BY created_at ASC",
            ).use { s ->
                ids.forEachIndexed { index, id -> s.setString(index + 1, id) }
                s.executeQuery().use { rs ->
                    val rows = mutableListOf<EnvelopeRow>()
                    while (rs.next()) rows += mapRow(rs)
                    return rows
                }
            }
        }
    }

    /** History for a conversation after a cursor (sync endpoint, MSG-06). */
    fun history(
        participantId: UUID,
        conversationId: String,
        afterSeq: Long,
        limit: Int,
    ): List<EnvelopeRow> {
        db.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT * FROM envelopes
                WHERE conversation_id = ? AND seq > ?
                  AND (recipient_id = ? OR sender_account_id = ?)
                  AND acked_at IS NULL
                ORDER BY seq ASC
                LIMIT ?
                """.trimIndent(),
            ).use { s ->
                s.setString(1, conversationId)
                s.setLong(2, afterSeq)
                s.setObject(3, participantId)
                s.setObject(4, participantId)
                s.setInt(5, limit)
                s.executeQuery().use { rs ->
                    val rows = mutableListOf<EnvelopeRow>()
                    while (rs.next()) rows += mapRow(rs)
                    return rows
                }
            }
        }
    }

    /** Delete-on-ack (MSG-04): removes envelopes the recipient acknowledged. */
    fun ack(ids: List<String>, recipientId: UUID, now: Long): Int {
        if (ids.isEmpty()) return 0
        val placeholders = ids.joinToString(",") { "?" }
        db.connection.use { connection ->
            connection.prepareStatement(
                "DELETE FROM envelopes WHERE id IN ($placeholders) AND recipient_id = ?",
            ).use { s ->
                ids.forEachIndexed { index, id -> s.setString(index + 1, id) }
                s.setObject(ids.size + 1, recipientId)
                return s.executeUpdate()
            }
        }
    }

    /** TTL purge (ops job): undelivered envelopes older than the window. */
    fun purgeOlderThan(cutoffMillis: Long): Int {
        db.connection.use { connection ->
            connection.prepareStatement("DELETE FROM envelopes WHERE created_at < ?")
                .use { s ->
                    s.setLong(1, cutoffMillis)
                    return s.executeUpdate()
                }
        }
    }

    private fun mapRow(rs: java.sql.ResultSet) = EnvelopeRow(
        id = rs.getString("id"),
        conversationId = rs.getString("conversation_id"),
        senderAccountId = rs.getObject("sender_account_id", UUID::class.java),
        senderDeviceId = rs.getLong("sender_device_id"),
        recipientId = rs.getObject("recipient_id", UUID::class.java),
        seq = rs.getLong("seq"),
        envelopeType = rs.getInt("envelope_type"),
        ciphertext = rs.getBytes("ciphertext"),
        clientMessageId = rs.getObject("client_message_id", UUID::class.java),
        createdAt = rs.getLong("created_at"),
        deliveredAt = rs.getObject("delivered_at") as Long?,
    )
}
