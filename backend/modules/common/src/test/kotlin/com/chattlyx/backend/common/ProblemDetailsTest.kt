package com.chattlyx.backend.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class ProblemDetailsTest {

    private val json = Json { encodeDefaults = true }

    @Test
    fun `serializes rfc9457 fields`() {
        val problem = ProblemDetails(
            title = "Not found",
            status = 404,
            code = "resource/not-found",
        )
        val encoded = json.encodeToString(ProblemDetails.serializer(), problem)
        assertTrue(encoded.contains("\"status\":404"))
        assertTrue(encoded.contains("\"code\":\"resource/not-found\""))
    }

    @Test
    fun `server exceptions never leak internal detail for 5xx`() {
        val exception = ChattlyxServerException.Internal(detail = "stack trace secrets")
        val problem = exception.toProblemDetails()
        assertEquals(500, problem.status)
        assertEquals(null, problem.detail)
    }

    @Test
    fun `429 carries retry hint`() {
        val problem = ChattlyxServerException.RateLimited(retryAfterMs = 30_000).toProblemDetails()
        assertEquals(429, problem.status)
        assertEquals(30_000L, problem.retryAfterMs)
    }
}
