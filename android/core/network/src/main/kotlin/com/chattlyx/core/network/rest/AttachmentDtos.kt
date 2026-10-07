package com.chattlyx.core.network.rest

import kotlinx.serialization.Serializable

/**
 * Phase 3 (MED-01..04) attachment REST DTOs, mirroring `POST /v1/attachments`
 * in backend/openapi/chattlyx-openapi.yaml (v0.3.0-phase3).
 */

@Serializable
data class DeclareAttachmentDto(
    /** "image" | "video" | "file" | "voice". */
    val kind: String,
    val mimeType: String,
    val sizeBytes: Long,
    /** SHA-256 of the ciphertext, lowercase hex. */
    val sha256: String,
    val recipientAccountId: String? = null,
    val conversationId: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Int? = null,
    val fileName: String? = null,
)

@Serializable
data class DeclaredAttachmentDto(
    val attachmentId: String,
    val uploadUrl: String,
)

@Serializable
data class AttachmentMetaDto(
    val attachmentId: String,
    val kind: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Int? = null,
    val fileName: String? = null,
    /** "pending" | "ready". */
    val status: String,
    val downloadUrl: String? = null,
)
