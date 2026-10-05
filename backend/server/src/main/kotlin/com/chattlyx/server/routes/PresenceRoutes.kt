package com.chattlyx.server.routes

import com.chattlyx.backend.common.ChattlyxServerException
import com.chattlyx.server.authdto.PresenceDto
import com.chattlyx.server.messaging.MessagingContext
import com.chattlyx.server.plugins.requireAccount
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.util.UUID

/**
 * STS presence: online flag + last-seen timestamp, presence TTLs kept in
 * Redis (master spec 8.4). Blocked peers' presence stays hidden (SAF).
 */
fun Application.installPresenceRoutes(messaging: MessagingContext, blocks: com.chattlyx.backend.db.BlockRepository) {
    routing {
        authenticate("chattlyx-bearer") {
            get("/v1/presence/{accountId}") {
                val principal = call.requireAccount()
                val target = try {
                    UUID.fromString(call.parameters["accountId"])
                } catch (e: IllegalArgumentException) {
                    throw ChattlyxServerException.Validation("accountId must be a UUID")
                }

                // SAF: if either side blocked the other, presence is hidden.
                if (blocks.isBlocked(principal.accountId, target) ||
                    blocks.isBlocked(target, principal.accountId)
                ) {
                    call.respond(PresenceDto(target.toString(), online = false, lastSeenMs = null))
                    return@get
                }

                val id = target.toString()
                call.respond(
                    PresenceDto(
                        accountId = id,
                        online = messaging.queues.isOnline(id),
                        lastSeenMs = messaging.queues.lastSeenMillis(id),
                    ),
                )
            }
        }
    }
}
