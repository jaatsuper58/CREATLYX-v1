package com.chattlyx.server

import com.chattlyx.server.dto.ServerConfigDto
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `liveness probe returns ok`() = testApplication {
        application { testModule() }
        val response = client.get("/health/live")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("OK", response.bodyAsText())
    }

    @Test
    fun `readiness probe returns ok`() = testApplication {
        application { testModule() }
        val response = client.get("/health/ready")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `config exposes limits and minimum version`() = testApplication {
        application { testModule() }
        val response = client.get("/v1/config")
        assertEquals(HttpStatusCode.OK, response.status)

        val config = json.decodeFromString(ServerConfigDto.serializer(), response.bodyAsText())
        assertEquals("0.1.0", config.minSupportedVersion)
        assertEquals(256, config.limits.maxGroupMembers)
        assertEquals(8_000, config.limits.maxMessageChars)
        assertEquals(2L * 1024 * 1024 * 1024, config.limits.maxAttachmentBytes)
    }

    @Test
    fun `unknown routes render rfc9457 problem json`() = testApplication {
        application { testModule() }
        val response = client.get("/v1/does-not-exist")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(response.bodyAsText().contains("\"code\":\"resource/not-found\""))
    }

    @Test
    fun `validation errors render 400 problem json`() = testApplication {
        application { testModule() }
        val response = client.get("/v1/_internal/validate-sample")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("validation/failed"))
    }
}
