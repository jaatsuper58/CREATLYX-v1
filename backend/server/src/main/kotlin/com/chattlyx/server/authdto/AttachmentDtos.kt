package com.chattlyx.server.authdto

import kotlinx.serialization.Serializable

/** Phase 3 (MED-*) attachment REST contracts. sha256 travels as hex. */

@Serializable
data class DeclareAttachmentBody(
    val kind: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256Hex: String,
    val recipientAccountId: String? = null,
    val conversationId: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Int? = null,
    val fileName: String? = null,
)

@Serializable
data class DeclareAttachmentResponse(
    val attachmentId: String,
    val uploadUrl: String,
)

@Serializable
data class AttachmentMetaDto(
    val attachmentId: String,
    val kind: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256Hex: String,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Int? = null,
    val fileName: String? = null,
    val status: String,
    val downloadUrl: String? = null,
)
