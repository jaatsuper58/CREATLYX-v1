package com.chattlyx.server.routes

import com.chattlyx.backend.common.ChattlyxServerException
import com.chattlyx.backend.db.BlockRepository
import com.chattlyx.server.authdto.BlockListResponse
import com.chattlyx.server.plugins.requireAccount
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.util.UUID

/** SAF block-list management. */
fun Application.installBlockRoutes(blocks: BlockRepository) {
    routing {
        authenticate("chattlyx-bearer") {
            get("/v1/blocks") {
                val principal = call.requireAccount()
                val ids = blocks.blockedBy(principal.accountId).map { it.toString() }
                call.respond(BlockListResponse(ids))
            }

            post("/v1/blocks/{accountId}") {
                val principal = call.requireAccount()
                val target = uuidParam()
                if (target == principal.accountId) {
                    throw ChattlyxServerException.Validation("cannot block yourself")
                }
                blocks.block(principal.accountId, target)
                call.respond(HttpStatusCode.Created)
            }

            delete("/v1/blocks/{accountId}") {
                val principal = call.requireAccount()
                val target = uuidParam()
                blocks.unblock(principal.accountId, target)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.uuidParam(): UUID = try {
    UUID.fromString(parameters["accountId"])
} catch (e: IllegalArgumentException) {
    throw ChattlyxServerException.Validation("accountId must be a UUID")
}
