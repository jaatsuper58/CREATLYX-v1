package com.chattlyx.server.routes

import com.chattlyx.backend.common.ChattlyxServerException
import com.chattlyx.server.attachments.AttachmentContext
import com.chattlyx.server.authdto.AttachmentMetaDto
import com.chattlyx.server.authdto.DeclareAttachmentBody
import com.chattlyx.server.authdto.DeclareAttachmentResponse
import com.chattlyx.server.plugins.requireAccount
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import java.util.UUID

/**
 * MED-01/02/03 attachment transport: declare -> PUT ciphertext -> meta/GET.
 * Bytes are ciphertext only; keys and the SHA-256 for verification travel
 * inside the E2EE AttachmentContent descriptor (MED-04).
 */
fun Application.installAttachmentRoutes(attachments: AttachmentContext) {
    val hex = java.util.HexFormat.of()

    routing {
        authenticate("chattlyx-bearer") {
            post("/v1/attachments") {
                val principal = call.requireAccount()
                val body = call.receive<DeclareAttachmentBody>()
                val sha256 = try {
                    hex.parseHex(body.sha256Hex)
                } catch (e: IllegalArgumentException) {
                    throw ChattlyxServerException.Validation("sha256Hex must be 64 hex chars")
                }
                val recipient = body.recipientAccountId?.let {
                    try {
                        UUID.fromString(it)
                    } catch (e: IllegalArgumentException) {
                        throw ChattlyxServerException.Validation("recipientAccountId must be a UUID")
                    }
                }
                val id = attachments.service.declare(
                    sender = principal.accountId,
                    kind = body.kind,
                    mimeType = body.mimeType,
                    sizeBytes = body.sizeBytes,
                    sha256 = sha256,
                    recipientAccountId = recipient,
                    conversationId = body.conversationId,
                    width = body.width,
                    height = body.height,
                    durationMs = body.durationMs,
                    fileName = body.fileName,
                )
                call.respond(
                    HttpStatusCode.Created,
                    DeclareAttachmentResponse(
                        attachmentId = id.toString(),
                        uploadUrl = "/v1/attachments/$id/data",
                    ),
                )
            }

            put("/v1/attachments/{id}/data") {
                val principal = call.requireAccount()
                val id = attachmentIdParam(call.parameters["id"])
                val bytes: ByteArray = call.receive()
                if (bytes.isEmpty()) {
                    throw ChattlyxServerException.Validation("empty attachment payload")
                }
                attachments.service.upload(principal.accountId, id, bytes)
                call.respond(HttpStatusCode.NoContent)
            }

            get("/v1/attachments/{id}") {
                val principal = call.requireAccount()
                val id = attachmentIdParam(call.parameters["id"])
                val row = attachments.service.meta(principal.accountId, id)
                call.respond(
                    AttachmentMetaDto(
                        attachmentId = row.id.toString(),
                        kind = row.kind,
                        mimeType = row.mimeType,
                        sizeBytes = row.sizeBytes,
                        sha256Hex = hex.formatHex(row.sha256),
                        width = row.width,
                        height = row.height,
                        durationMs = row.durationMs,
                        fileName = row.fileName,
                        status = row.status,
                        downloadUrl = if (row.isReady) "/v1/attachments/$id/data" else null,
                    ),
                )
            }

            get("/v1/attachments/{id}/data") {
                val principal = call.requireAccount()
                val id = attachmentIdParam(call.parameters["id"])
                val (row, bytes) = attachments.service.download(principal.accountId, id)
                val contentType = ContentType.parse(row.mimeType)
                    .getOrDefault(ContentType.Application.OctetStream)
                call.respondBytes(bytes, contentType)
            }
        }
    }
}

private fun attachmentIdParam(raw: String?): UUID {
    return try {
        UUID.fromString(raw ?: "")
    } catch (e: IllegalArgumentException) {
        throw ChattlyxServerException.Validation("attachment id must be a UUID")
    }
}
