package com.chattlyx.backend.common

import kotlinx.serialization.Serializable

/** RFC 9457 media type for structured errors. */
const val PROBLEM_CONTENT_TYPE = "application/problem+json"

/**
 * RFC 9457 problem details — the single error shape for every REST response
 * (master spec Section 8.1).
 */
@Serializable
data class ProblemDetails(
    val type: String = "about:blank",
    val title: String,
    val status: Int,
    val detail: String? = null,
    val instance: String? = null,
    /** Stable machine code for clients, e.g. "auth/otp-expired". */
    val code: String? = null,
    /** Milliseconds to wait before retrying, when applicable. */
    val retryAfterMs: Long? = null,
)
