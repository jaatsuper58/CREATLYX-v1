package com.chattlyx.server.routes

import com.chattlyx.backend.auth.AuthServices
import com.chattlyx.backend.common.ChattlyxServerException
import com.chattlyx.server.authdto.KeyBundleDto
import com.chattlyx.server.authdto.KeyCountDto
import com.chattlyx.server.authdto.PrekeyDto
import com.chattlyx.server.authdto.SignedPrekeyDto
import com.chattlyx.server.authdto.UploadKeysBody
import com.chattlyx.server.plugins.requireAccount
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import java.util.Base64
import java.util.UUID

/**
 * AUTH-06 key management: upload bundles on registration/replenish, fetch a
 * bundle to start a session, count one-time prekeys. Public material only.
 */
fun Application.installKeyRoutes(auth: AuthServices) {
    val decoder = Base64.getDecoder()
    val encoder = Base64.getEncoder()

    routing {
        authenticate("chattlyx-bearer") {
            put("/v1/devices/keys") {
                val principal = call.requireAccount()
                val body = call.receive<UploadKeysBody>()
                val now = System.currentTimeMillis()
                val keys = auth.keyRepository

                keys.storeIdentityKey(
                    principal.accountId,
                    principal.deviceId,
                    decoder.decode(body.identityKey),
                    now,
                )
                keys.storeSignedPreKey(
                    principal.accountId,
                    principal.deviceId,
                    body.signedPrekey.prekeyId,
                    decoder.decode(body.signedPrekey.record),
                    now,
                )
                keys.storeOneTimePreKeys(
                    principal.accountId,
                    principal.deviceId,
                    body.oneTimePrekeys.associate { it.prekeyId to decoder.decode(it.record) },
                )
                keys.storeKyberPreKeys(
                    principal.accountId,
                    principal.deviceId,
                    body.kyberPrekeys.associate { it.prekeyId to decoder.decode(it.record) },
                )
                auth.auditRepository.record(
                    principal.accountId,
                    "keys.uploaded",
                    "otp=${body.oneTimePrekeys.size} kyber=${body.kyberPrekeys.size}",
                )
                call.respond(KeyCountDto(keys.oneTimePreKeyCount(principal.accountId, principal.deviceId), keys.kyberPreKeyCount(principal.accountId, principal.deviceId)))
            }

            get("/v1/keys/count") {
                val principal = call.requireAccount()
                val keys = auth.keyRepository
                call.respond(
                    KeyCountDto(
                        oneTimePrekeys = keys.oneTimePreKeyCount(principal.accountId, principal.deviceId),
                        kyberPrekeys = keys.kyberPreKeyCount(principal.accountId, principal.deviceId),
                    ),
                )
            }

            get("/v1/keys/{accountId}/{deviceId}") {
                val principal = call.requireAccount()
                val targetAccount = call.parameters["accountId"]?.let {
                    try {
                        UUID.fromString(it)
                    } catch (e: IllegalArgumentException) {
                        throw ChattlyxServerException.Validation("accountId must be a UUID")
                    }
                } ?: throw ChattlyxServerException.Validation("accountId required")
                val targetDevice = call.parameters["deviceId"]?.toLongOrNull()
                    ?: throw ChattlyxServerException.Validation("deviceId must be numeric")

                // Blocked-user lookups are enforced once block lists land (CON-05).
                val keys = auth.keyRepository
                val identity = keys.identityKey(targetAccount, targetDevice)
                val signed = keys.signedPreKey(targetAccount, targetDevice)
                val oneTime = keys.consumeOneTimePreKey(targetAccount, targetDevice)
                val kyber = keys.consumeKyberPreKey(targetAccount, targetDevice)

                if (identity == null && signed == null) {
                    throw ChattlyxServerException.NotFound("no key bundle for device", "keys/no-bundle")
                }

                call.respond(
                    KeyBundleDto(
                        accountId = targetAccount.toString(),
                        deviceId = targetDevice,
                        identityKey = identity?.let(encoder::encodeToString),
                        signedPrekey = signed?.let { (id, record) ->
                            SignedPrekeyDto(id, encoder.encodeToString(record))
                        },
                        oneTimePrekey = oneTime?.let { (id, record) ->
                            PrekeyDto(id, encoder.encodeToString(record))
                        },
                        kyberPrekey = kyber?.let { (id, record) ->
                            PrekeyDto(id, encoder.encodeToString(record))
                        },
                    ),
                )
                // Touch our own device presence on activity.
                auth.deviceRepository.touch(principal.accountId, principal.deviceId, System.currentTimeMillis())
            }
        }
    }
}
