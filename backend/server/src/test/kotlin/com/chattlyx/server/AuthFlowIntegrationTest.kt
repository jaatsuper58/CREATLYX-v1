package com.chattlyx.server

import com.chattlyx.backend.auth.AuthServiceConfig
import com.chattlyx.backend.auth.AuthServices
import com.chattlyx.backend.db.DbConfig
import com.chattlyx.backend.db.DbFactory
import com.chattlyx.backend.db.SchemaMigrator
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Phase 1 acceptance: register, upload keys, fetch bundle, refresh rotation,
 * rate limits, profile, devices and deletion — end to end on real Postgres.
 */
@Testcontainers
class AuthFlowIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun startApp(block: suspend (io.ktor.client.HttpClient) -> Unit) {
        val dataSource = DbFactory.create(
            DbConfig(
                jdbcUrl = postgres.jdbcUrl,
                username = postgres.username,
                password = postgres.password,
            ),
        )
        try {
            SchemaMigrator(dataSource).migrate()
            val services = AuthServices.create(
                AuthServiceConfig(
                    jwtSecret = "integration-secret-0123456789-abcd",
                    e164Pepper = "integration-pepper",
                    e164KeyBase64 = Base64.getEncoder().encodeToString(ByteArray(32) { 9 }),
                    devMode = true,
                ),
                dataSource,
            )

            testApplication {
                application { moduleWithContext(services) }
                block(client)
            }
        } finally {
            dataSource.close()
        }
    }

    private suspend fun register(client: io.ktor.client.HttpClient, e164: String): String {
        val request = client.post("/v1/auth/otp/request") {
            contentType(ContentType.Application.Json)
            setBody("""{"e164":"$e164"}""")
        }
        assertEquals(HttpStatusCode.OK, request.status)

        val verify = client.post("/v1/auth/otp/verify") {
            contentType(ContentType.Application.Json)
            setBody("""{"e164":"$e164","code":"111111","deviceName":"Integration device"}""")
        }
        assertEquals(HttpStatusCode.OK, verify.status)
        val obj = json.parseToJsonElement(verify.bodyAsText()).jsonObject
        return assertNotNull(obj["accessToken"]).jsonPrimitive.content
    }

    @Test
    fun `full registration keys profile devices deletion flow`() = startApp { client ->
        val e164 = "+919876543210"
        val token = register(client, e164)

        // AUTH-06: upload a key bundle.
        val upload = client.put("/v1/devices/keys") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "identityKey": "aWRlbnRpdHk=",
                  "signedPrekey": {"prekeyId": 1, "record": "c2lnbmVk"},
                  "oneTimePrekeys": [
                    {"prekeyId": 100, "record": "b3RwMQ=="},
                    {"prekeyId": 101, "record": "b3RwMg=="}
                  ],
                  "kyberPrekeys": [{"prekeyId": 7, "record": "a3liZXI="}]
                }
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.OK, upload.status)
        assertTrue(upload.bodyAsText().contains("\"oneTimePrekeys\":2"))

        // Count + consume on fetch.
        val count = client.get("/v1/keys/count") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, count.status)
        assertTrue(count.bodyAsText().contains("\"oneTimePrekeys\":2"))

        // Second account fetches the bundle (prekeys consumed).
        val secondToken = register(client, "+919876543211")
        val me = client.get("/v1/profile") { bearerAuth(token) }
        val accountId = json.parseToJsonElement(me.bodyAsText())
            .jsonObject["accountId"]!!.jsonPrimitive.content

        // Device id for the first registration is 1 (per-number sequence in this test DB).
        val bundle = client.get("/v1/keys/$accountId/1") { bearerAuth(secondToken) }
        assertEquals(HttpStatusCode.OK, bundle.status)
        val bundleBody = bundle.bodyAsText()
        assertTrue(bundleBody.contains("aWRlbnRpdHk="))
        assertTrue(bundleBody.contains("oneTimePrekey"))

        val countAfter = client.get("/v1/keys/count") { bearerAuth(token) }
        assertTrue(countAfter.bodyAsText().contains("\"oneTimePrekeys\":1"))

        // AUTH-04: profile update incl. username uniqueness.
        val update = client.put("/v1/profile") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"displayName":"Asha","username":"asha_v","about":"Hello"}""")
        }
        assertEquals(HttpStatusCode.OK, update.status)
        assertTrue(update.bodyAsText().contains("Asha"))

        val taken = client.put("/v1/profile") {
            bearerAuth(secondToken)
            contentType(ContentType.Application.Json)
            setBody("""{"displayName":"Other","username":"asha_v"}""")
        }
        assertEquals(HttpStatusCode.Conflict, taken.status)

        // AUTH-07: devices list shows current device.
        val devices = client.get("/v1/devices") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, devices.status)
        assertTrue(devices.bodyAsText().contains("\"current\":true"))

        // AUTH-10: deletion; subsequent token refresh still valid until revoked? ->
        // deletion revokes all refresh tokens; bearer token dies on device revocation.
        val delete = client.delete("/v1/account") { bearerAuth(token) }
        assertEquals(HttpStatusCode.NoContent, delete.status)
    }

    @Test
    fun `otp verify rejects wrong code and enforces attempt limits`() = startApp { client ->
        val e164 = "+919876543212"
        client.post("/v1/auth/otp/request") {
            contentType(ContentType.Application.Json)
            setBody("""{"e164":"$e164"}""")
        }

        val wrong = client.post("/v1/auth/otp/verify") {
            contentType(ContentType.Application.Json)
            setBody("""{"e164":"$e164","code":"000000"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, wrong.status)

        // Exhaust remaining attempts -> lockout returns 429.
        repeat(4) {
            client.post("/v1/auth/otp/verify") {
                contentType(ContentType.Application.Json)
                setBody("""{"e164":"$e164","code":"000000"}""")
            }
        }
        val locked = client.post("/v1/auth/otp/verify") {
            contentType(ContentType.Application.Json)
            setBody("""{"e164":"$e164","code":"111111"}""")
        }
        assertEquals(HttpStatusCode.TooManyRequests, locked.status)
    }

    @Test
    fun `refresh rotation detects reuse`() = startApp { client ->
        val e164 = "+919876543213"
        client.post("/v1/auth/otp/request") {
            contentType(ContentType.Application.Json)
            setBody("""{"e164":"$e164"}""")
        }
        val verify = client.post("/v1/auth/otp/verify") {
            contentType(ContentType.Application.Json)
            setBody("""{"e164":"$e164","code":"111111","deviceName":"t"}""")
        }
        val refresh = json.parseToJsonElement(verify.bodyAsText())
            .jsonObject["refreshToken"]!!.jsonPrimitive.content

        val first = client.post("/v1/auth/token/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$refresh"}""")
        }
        assertEquals(HttpStatusCode.OK, first.status)

        // Reuse of the rotated token must fail (and revokes the device).
        val reused = client.post("/v1/auth/token/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$refresh"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, reused.status)
    }

    @Test
    fun `protected routes reject missing or invalid bearer`() = startApp { client ->
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/profile").status)
        val bad = client.get("/v1/profile") { bearerAuth("garbage") }
        assertEquals(HttpStatusCode.Unauthorized, bad.status)
    }
}
