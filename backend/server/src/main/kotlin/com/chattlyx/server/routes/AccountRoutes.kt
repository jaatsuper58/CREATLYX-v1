package com.chattlyx.server.routes

import com.chattlyx.backend.auth.AuthServices
import com.chattlyx.backend.common.ChattlyxServerException
import com.chattlyx.backend.common.UuidV7
import com.chattlyx.server.authdto.AvatarUploadResultDto
import com.chattlyx.server.authdto.DeviceDto
import com.chattlyx.server.authdto.DeviceListDto
import com.chattlyx.server.authdto.ProfileDto
import com.chattlyx.server.authdto.ProfileUpdateBody
import com.chattlyx.server.plugins.requireAccount
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import java.util.UUID

private val USERNAME_PATTERN = Regex("^[a-z0-9_]{3,32}$")
private const val MAX_AVATAR_CIPHERTEXT = 400_000

/** AUTH-04 profile, AUTH-07 devices, AUTH-10 deletion, avatar blobs. */
fun Application.installAccountRoutes(auth: AuthServices) {
    routing {
        authenticate("chattlyx-bearer") {
            get("/v1/profile") {
                val principal = call.requireAccount()
                val account = auth.accountRepository.findById(principal.accountId)
                    ?: throw ChattlyxServerException.NotFound("account")
                call.respond(
                    ProfileDto(
                        accountId = account.id.toString(),
                        displayName = account.displayName,
                        username = account.username,
                        about = account.about,
                        avatarBlobId = account.avatarBlobId?.toString(),
                        e164Masked = null, // number never echoed back
                    ),
                )
            }

            put("/v1/profile") {
                val principal = call.requireAccount()
                val body = call.receive<ProfileUpdateBody>()

                val displayName = body.displayName.trim()
                if (displayName.isEmpty() || displayName.length > 40) {
                    throw ChattlyxServerException.Validation("displayName must be 1-40 chars")
                }
                if (body.about.length > 140) {
                    throw ChattlyxServerException.Validation("about must be <= 140 chars")
                }

                val username = body.username?.lowercase()?.trim()?.ifEmpty { null }
                if (username != null) {
                    if (!USERNAME_PATTERN.matches(username)) {
                        throw ChattlyxServerException.Validation("username must match [a-z0-9_]{3,32}")
                    }
                    val owner = auth.accountRepository.findIdByUsername(username)
                    if (owner != null && owner != principal.accountId) {
                        throw ChattlyxServerException.Conflict("username taken", "profile/username-taken")
                    }
                }

                auth.accountRepository.updateProfile(
                    id = principal.accountId,
                    displayName = displayName,
                    about = body.about,
                    username = username,
                    avatarBlobId = body.avatarBlobId?.let(UUID::fromString),
                )
                auth.auditRepository.record(principal.accountId, "profile.updated")
                val updated = auth.accountRepository.findById(principal.accountId)
                    ?: throw ChattlyxServerException.NotFound("account")
                call.respond(
                    ProfileDto(
                        accountId = updated.id.toString(),
                        displayName = updated.displayName,
                        username = updated.username,
                        about = updated.about,
                        avatarBlobId = updated.avatarBlobId?.toString(),
                    ),
                )
            }

            post("/v1/profile/avatar") {
                val principal = call.requireAccount()
                val bytes: ByteArray = call.receive()
                if (bytes.isEmpty()) {
                    throw ChattlyxServerException.Validation("empty avatar payload")
                }
                if (bytes.size > MAX_AVATAR_CIPHERTEXT) {
                    throw ChattlyxServerException.Validation("avatar ciphertext exceeds limit")
                }

                val blobId = UuidV7.generate()
                auth.avatarBlobRepository.store(
                    id = blobId,
                    ownerId = principal.accountId,
                    ciphertext = bytes,
                    now = System.currentTimeMillis(),
                )
                auth.accountRepository.updateProfile(
                    id = principal.accountId,
                    displayName = auth.accountRepository.findById(principal.accountId)?.displayName.orEmpty(),
                    about = auth.accountRepository.findById(principal.accountId)?.about.orEmpty(),
                    username = auth.accountRepository.findById(principal.accountId)?.username,
                    avatarBlobId = blobId,
                )
                call.respond(HttpStatusCode.Created, AvatarUploadResultDto(blobId.toString()))
            }

            get("/v1/profile/avatar/{blobId}") {
                val blobId = call.parameters["blobId"]?.let { UUID.fromString(it) }
                    ?: throw ChattlyxServerException.Validation("blobId must be a UUID")
                val blob = auth.avatarBlobRepository.fetch(blobId)
                    ?: throw ChattlyxServerException.NotFound("avatar")
                // Ciphertext only; the key travels inside the E2EE profile payload.
                call.respondBytes(blob, io.ktor.http.ContentType.Application.OctetStream)
            }

            get("/v1/devices") {
                val principal = call.requireAccount()
                val rows = auth.deviceRepository.listActive(principal.accountId)
                call.respond(
                    DeviceListDto(
                        devices = rows.map {
                            DeviceDto(
                                deviceId = it.id,
                                name = it.name,
                                createdAt = it.createdAt,
                                lastSeenAt = it.lastSeenAt,
                                current = it.id == principal.deviceId,
                            )
                        },
                    ),
                )
            }

            delete("/v1/devices/{deviceId}") {
                val principal = call.requireAccount()
                val deviceId = call.parameters["deviceId"]?.toLongOrNull()
                    ?: throw ChattlyxServerException.Validation("deviceId must be numeric")
                if (deviceId == principal.deviceId) {
                    throw ChattlyxServerException.Validation("use DELETE /v1/account to remove this device")
                }
                val revoked = auth.deviceRepository.revoke(principal.accountId, deviceId, System.currentTimeMillis())
                if (!revoked) throw ChattlyxServerException.NotFound("device")
                auth.tokenService.revokeDevice(principal.accountId, deviceId)
                call.respond(HttpStatusCode.NoContent)
            }

            delete("/v1/account") {
                val principal = call.requireAccount()
                // AUTH-10: soft-delete now, purge within 30 days (ops job).
                auth.tokenService.revokeAll(principal.accountId)
                auth.accountRepository.markDeleted(principal.accountId, System.currentTimeMillis())
                auth.auditRepository.record(principal.accountId, "account.deleted")
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
