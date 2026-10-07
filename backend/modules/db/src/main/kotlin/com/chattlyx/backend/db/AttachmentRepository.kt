package com.chattlyx.backend.db

import java.util.UUID
import javax.sql.DataSource

/** Attachment declaration row (MED-*). Blob bytes live in the blob store. */
data class AttachmentRow(
    val id: UUID,
    val senderAccountId: UUID,
    val recipientAccountId: UUID?,
    val conversationId: String?,
    val kind: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: ByteArray,
    val width: Int?,
    val height: Int?,
    val durationMs: Int?,
    val fileName: String?,
    val status: String,
    val createdAt: Long,
    val uploadedAt: Long?,
) {
    val isReady: Boolean get() = status == STATUS_READY

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_READY = "ready"
    }
}

/**
 * Phase 3 attachment metadata store. The server only ever sees ciphertext
 * blobs and opaque metadata; access control is sender-or-recipient.
 */
class AttachmentRepository(private val db: DataSource) {

    fun insert(row: AttachmentRow) {
        db.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO attachments
                (id, sender_account_id, recipient_account_id, conversation_id, kind, mime_type,
                 size_bytes, sha256, width, height, duration_ms, file_name, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { s ->
                s.setObject(1, row.id)
                s.setObject(2, row.senderAccountId)
                s.setObject(3, row.recipientAccountId)
                s.setString(4, row.conversationId)
                s.setString(5, row.kind)
                s.setString(6, row.mimeType)
                s.setLong(7, row.sizeBytes)
                s.setBytes(8, row.sha256)
                s.setObject(9, row.width)
                s.setObject(10, row.height)
                s.setObject(11, row.durationMs)
                s.setString(12, row.fileName)
                s.setString(13, row.status)
                s.setLong(14, row.createdAt)
                s.executeUpdate()
            }
        }
    }

    fun findById(id: UUID): AttachmentRow? {
        db.connection.use { connection ->
            connection.prepareStatement("SELECT * FROM attachments WHERE id = ?").use { s ->
                s.setObject(1, id)
                s.executeQuery().use { rs ->
                    return if (rs.next()) map(rs) else null
                }
            }
        }
    }

    /** Marks a pending attachment as uploaded; returns false when already ready/absent. */
    fun markReady(id: UUID, uploadedAt: Long): Boolean {
        db.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE attachments SET status = ?, uploaded_at = ? WHERE id = ? AND status = ?",
            ).use { s ->
                s.setString(1, AttachmentRow.STATUS_READY)
                s.setLong(2, uploadedAt)
                s.setObject(3, id)
                s.setString(4, AttachmentRow.STATUS_PENDING)
                return s.executeUpdate() == 1
            }
        }
    }

    /**
     * Access check (MED-*): the sender always, the declared recipient once
     * set. Attachment ids are capability tokens distributed only inside E2EE
     * payloads, so authenticated possession is sufficient. Blobs bound to a
     * group conversation carry no recipient; AttachmentService layers the
     * live-membership check on top of this.
     */
    fun canAccess(row: AttachmentRow, accountId: UUID): Boolean =
        row.senderAccountId == accountId || row.recipientAccountId == accountId

    private fun map(rs: java.sql.ResultSet): AttachmentRow = AttachmentRow(
        id = rs.getObject("id", UUID::class.java),
        senderAccountId = rs.getObject("sender_account_id", UUID::class.java),
        recipientAccountId = rs.getObject("recipient_account_id", UUID::class.java),
        conversationId = rs.getString("conversation_id"),
        kind = rs.getString("kind"),
        mimeType = rs.getString("mime_type"),
        sizeBytes = rs.getLong("size_bytes"),
        sha256 = rs.getBytes("sha256"),
        width = rs.getObject("width", Int::class.javaObjectType),
        height = rs.getObject("height", Int::class.javaObjectType),
        durationMs = rs.getObject("duration_ms", Int::class.javaObjectType),
        fileName = rs.getString("file_name"),
        status = rs.getString("status"),
        createdAt = rs.getLong("created_at"),
        uploadedAt = rs.getObject("uploaded_at", Long::class.javaObjectType),
    )
}
