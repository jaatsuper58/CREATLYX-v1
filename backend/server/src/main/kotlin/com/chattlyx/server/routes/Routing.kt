package com.chattlyx.server.routes

import com.chattlyx.backend.common.ChattlyxServerException
import com.chattlyx.server.dto.defaultServerConfig
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

/**
 * Phase 0 routes: liveness probes + /v1/config. Auth, keys, messages, media,
 * groups, calls and safety routes land with their phases per the OpenAPI
 * contract (backend/openapi/chattlyx-openapi.yaml).
 */
fun Application.configureRouting() {
    routing {
        get("/health/live") {
            call.respondText("OK")
        }

        get("/health/ready") {
            // Phase 0: no external dependencies wired yet; reports OK.
            call.respondText("OK")
        }

        get("/v1/config") {
            call.respond(defaultServerConfig())
        }

        // Synthetic endpoint demonstrating the RFC 9457 validation path.
        get("/v1/_internal/validate-sample") {
            throw ChattlyxServerException.Validation("sample is not a real resource")
        }
    }
}
