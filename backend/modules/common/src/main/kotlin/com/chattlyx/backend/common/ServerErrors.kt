package com.chattlyx.backend.common

/**
 * Server-side error taxonomy. Throw from services; the StatusPages plugin
 * renders RFC 9457 responses. Codes are stable contracts for clients.
 */
sealed class ChattlyxServerException(
    val statusCode: Int,
    val code: String,
    message: String,
    val retryAfterMs: Long? = null,
) : RuntimeException(message) {

    class Validation(detail: String, code: String = "validation/failed") :
        ChattlyxServerException(400, code, detail)

    class Unauthorized(detail: String = "Authentication required", code: String = "auth/unauthorized") :
        ChattlyxServerException(401, code, detail)

    class Forbidden(detail: String, code: String = "auth/forbidden") :
        ChattlyxServerException(403, code, detail)

    class NotFound(detail: String, code: String = "resource/not-found") :
        ChattlyxServerException(404, code, detail)

    class Conflict(detail: String, code: String = "resource/conflict") :
        ChattlyxServerException(409, code, detail)

    class RateLimited(retryAfterMs: Long?, code: String = "rate-limited") :
        ChattlyxServerException(429, code, "Too many requests", retryAfterMs)

    class Internal(detail: String = "Internal error", code: String = "server/internal") :
        ChattlyxServerException(500, code, detail)

    fun toProblemDetails(instance: String? = null) = ProblemDetails(
        title = message ?: "Error",
        status = statusCode,
        detail = if (statusCode < 500) message else null, // never leak 5xx internals
        instance = instance,
        code = code,
        retryAfterMs = retryAfterMs,
    )
}
