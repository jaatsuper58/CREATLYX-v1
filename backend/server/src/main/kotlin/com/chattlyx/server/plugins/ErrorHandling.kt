package com.chattlyx.server.plugins

import com.chattlyx.backend.common.ChattlyxServerException
import com.chattlyx.backend.common.PROBLEM_CONTENT_TYPE
import com.chattlyx.backend.common.ProblemDetails
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respond
import org.slf4j.LoggerFactory

/** Maps the server error taxonomy to RFC 9457 problem+json responses. */
fun Application.configureErrorHandling() {
    val logger = LoggerFactory.getLogger("chattlyx.errors")

    install(StatusPages) {
        exception<ChattlyxServerException> { call, cause ->
            val problem = cause.toProblemDetails(instance = call.request.path())
            val retryAfterMs = problem.retryAfterMs
            if (retryAfterMs != null) {
                call.response.header("Retry-After", (retryAfterMs / 1000).toString())
            }
            call.response.header("Content-Type", PROBLEM_CONTENT_TYPE)
            call.respond(HttpStatusCode.fromValue(problem.status), problem)
        }

        status(HttpStatusCode.NotFound) { call, _ ->
            val problem = ProblemDetails(
                title = "Not found",
                status = 404,
                instance = call.request.path(),
                code = "resource/not-found",
            )
            call.response.header("Content-Type", PROBLEM_CONTENT_TYPE)
            call.respond(HttpStatusCode.NotFound, problem)
        }

        exception<Throwable> { call, cause ->
            logger.error("Unhandled error on ${call.request.path()}", cause)
            val problem = ProblemDetails(
                title = "Internal error",
                status = 500,
                instance = call.request.path(),
                code = "server/internal",
            )
            call.response.header("Content-Type", PROBLEM_CONTENT_TYPE)
            call.respond(HttpStatusCode.InternalServerError, problem)
        }
    }
}
