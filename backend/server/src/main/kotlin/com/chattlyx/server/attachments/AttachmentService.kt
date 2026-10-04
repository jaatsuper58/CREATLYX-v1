package com.chattlyx.server.attachments

import com.chattlyx.backend.common.ChattlyxServerException
import com.chattlyx.backend.common.UuidV7
import com.chattlyx.backend.db.AttachmentRepository
import com.chattlyx.backend.db.AttachmentRow
import com.chattlyx.backend.storage.BlobStore
import java.util.UUID

/**
 * MED-* attachment lifecycle: declare -> upload ciphertext -> fetch.
 *
 * The server never sees plaintext: clients encrypt with the session key
 * before PUT and verify the SHA-256 (carried inside the E2EE descriptor)
 * after GET. Attachment ids are capability tokens — they only travel inside
 * encrypted message payloads, so authenticated possession implies access.
 */
class AttachmentService(
    private val repository: AttachmentRepository,
    private val blobStore: BlobStore,
    private val maxAttachmentBytes: Long,
) {

    /** Registers intent and returns the new attachment id. */
    fun declare(
        sender: UUID,
        kind: String,
        mimeType: String,
        sizeBytes: Long,
        sha256: ByteArray,
        recipientAccountId: UUID?,
        conversationId: String?,
        width: Int?,
        height: Int?,
        durationMs: Int?,
        fileName: String?,
    ): UUID {
        if (kind !in ALLOWED_KINDS) {
            throw ChattlyxServerException.Validation("unknown attachment kind")
        }
        if (mimeType.isBlank() || mimeType.length > MAX_MIME_LENGTH) {
            throw ChattlyxServerException.Validation("mimeType missing or too long")
        }
        if (sizeBytes < 1 || sizeBytes > maxAttachmentBytes) {
            throw ChattlyxServerException.Validation("sizeBytes outside allowed range")
        }
        if (sha256.size != SHA256_BYTES) {
            throw ChattlyxServerException.Validation("sha256 must be 32 bytes")
        }
        if (fileName != null && fileName.length > MAX_FILENAME_LENGTH) {
            throw ChattlyxServerException.Validation("fileName too long")
        }
        if (width != null && (width <= 0 || width > MAX_DIMENSION)) {
            throw ChattlyxServerException.Validation("width out of range")
        }
        if (height != null && (height <= 0 || height > MAX_DIMENSION)) {
            throw ChattlyxServerException.Validation("height out of range")
        }
        if (durationMs != null && (durationMs < 0 || durationMs > MAX_DURATION_MS)) {
            throw ChattlyxServerException.Validation("durationMs out of range")
        }

        val id = UuidV7.generate()
        repository.insert(
            AttachmentRow(
                id = id,
                senderAccountId = sender,
                recipientAccountId = recipientAccountId,
                conversationId = conversationId,
                kind = kind,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                sha256 = sha256,
                width = width,
                height = height,
                durationMs = durationMs,
                fileName = fileName,
                status = AttachmentRow.STATUS_PENDING,
                createdAt = System.currentTimeMillis(),
                uploadedAt = null,
            ),
        )
        return id
    }

    /** Stores the ciphertext blob; sender-only, pending-only, exact size. */
    fun upload(sender: UUID, id: UUID, bytes: ByteArray) {
        val row = repository.findById(id)
            ?: throw ChattlyxServerException.NotFound("attachment")
        if (row.senderAccountId != sender) {
            throw ChattlyxServerException.Forbidden("only the sender may upload")
        }
        if (row.isReady) {
            throw ChattlyxServerException.Conflict("attachment already uploaded")
        }
        if (bytes.size.toLong() != row.sizeBytes) {
            throw ChattlyxServerException.Validation("upload size differs from declared size")
        }
        blobStore.put(id.toString(), bytes)
        if (!repository.markReady(id, System.currentTimeMillis())) {
            throw ChattlyxServerException.Conflict("attachment already uploaded")
        }
    }

    /** Metadata for sender or recipient. */
    fun meta(requester: UUID, id: UUID): AttachmentRow {
        val row = repository.findById(id)
            ?: throw ChattlyxServerException.NotFound("attachment")
        if (!repository.canAccess(row, requester)) {
            // Do not leak existence to strangers.
            throw ChattlyxServerException.NotFound("attachment")
        }
        return row
    }

    /** Ciphertext bytes for sender or recipient; 404 until uploaded. */
    fun download(requester: UUID, id: UUID): Pair<AttachmentRow, ByteArray> {
        val row = meta(requester, id)
        if (!row.isReady) {
            throw ChattlyxServerException.NotFound("attachment data")
        }
        val bytes = blobStore.get(id.toString())
            ?: throw ChattlyxServerException.NotFound("attachment data")
        return row to bytes
    }

    companion object {
        val ALLOWED_KINDS = setOf("image", "video", "file", "voice")
        const val SHA256_BYTES = 32
        const val MAX_MIME_LENGTH = 128
        const val MAX_FILENAME_LENGTH = 255
        const val MAX_DIMENSION = 16_384
        const val MAX_DURATION_MS = 24 * 60 * 60 * 1000
    }
}
