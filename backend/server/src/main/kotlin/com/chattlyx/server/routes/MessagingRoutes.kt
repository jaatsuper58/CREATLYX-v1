package com.chattlyx.server.routes

import com.chattlyx.backend.auth.AuthServices
import com.chattlyx.backend.common.ChattlyxServerException
import com.chattlyx.server.authdto.DiscoveredContactDto
import com.chattlyx.server.authdto.DiscoveryRequestBody
import com.chattlyx.server.authdto.DiscoveryResponseDto
import com.chattlyx.server.authdto.HistoryResponseDto
import com.chattlyx.server.authdto.PushTokenBody
import com.chattlyx.server.authdto.SyncEnvelopeDto
import com.chattlyx.server.messaging.MessagingContext
import com.chattlyx.server.plugins.requireAccount
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import java.util.Base64

/** CON-03 discovery, NOT-01 push registration, MSG-06 history sync. */
fun Application.installMessagingRoutes(auth: AuthServices, messaging: MessagingContext) {
    val encoder = Base64.getEncoder()

    routing {
        authenticate("chattlyx-bearer") {
            post("/v1/contacts/discovery") {
                val body = call.receive<DiscoveryRequestBody>()
                val matches = messaging.contactsService.discover(body.hashes)
                call.respond(
                    DiscoveryResponseDto(
                        matches = matches.map {
                            DiscoveredContactDto(
                                accountId = it.accountId.toString(),
                                displayName = it.displayName,
                                username = it.username,
                                avatarBlobId = it.avatarBlobId?.toString(),
                            )
                        },
                    ),
                )
            }

            put("/v1/devices/push") {
                val principal = call.requireAccount()
                val body = call.receive<PushTokenBody>()
                if (body.token.isBlank() || body.token.length > 512) {
                    throw ChattlyxServerException.Validation("push token malformed")
                }
                auth.deviceRepository.setPushToken(
                    principal.accountId,
                    principal.deviceId,
                    body.token,
                    System.currentTimeMillis(),
                )
                call.respond(io.ktor.http.HttpStatusCode.NoContent)
            }

            get("/v1/messages/{conversationId}") {
                val principal = call.requireAccount()
                val conversationId = call.parameters["conversationId"]
                    ?: throw ChattlyxServerException.Validation("conversationId required")
                val afterSeq = call.request.queryParameters["afterSeq"]?.toLongOrNull() ?: 0L
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100

                val envelopes = messaging.messagingService.history(
                    participant = principal.accountId,
                    conversationId = conversationId,
                    afterSeq = afterSeq,
                    limit = limit,
                )
                call.respond(
                    HistoryResponseDto(
                        conversationId = conversationId,
                        envelopes = envelopes.map { SyncEnvelopeDto(encoder.encodeToString(it.toByteArray())) },
                        complete = envelopes.size < limit,
                    ),
                )
            }
        }
    }
}
