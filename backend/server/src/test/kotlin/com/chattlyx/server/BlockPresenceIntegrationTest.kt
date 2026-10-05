package com.chattlyx.server

import com.chattlyx.backend.auth.AuthServiceConfig
import com.chattlyx.backend.auth.AuthServices
import com.chattlyx.backend.db.BlockRepository
import com.chattlyx.backend.db.DbConfig
import com.chattlyx.backend.db.DbFactory
import com.chattlyx.backend.db.SchemaMigrator
import com.chattlyx.backend.redis.RedisConfig
import com.chattlyx.server.messaging.MessagingContext
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/** Phase 6 acceptance (SAF/STS): block list + presence hiding, real stores. */
@Testcontainers
class BlockPresenceIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")

        @Container
        @JvmStatic
        val redis: GenericContainer<*> =
            GenericContainer("redis:7-alpine").withExposedPorts(6379)
    }

    private val json = Json { ignoreUnknownKeys = true }

    private data class Account(val token: String, val accountId: String)

    private suspend fun register(client: io.ktor.client.HttpClient, e164: String): Account {
        client.post("/v1/auth/otp/request") {
            contentType(ContentType.Application.Json)
            setBody("""{"e164":"$e164"}""")
        }
        val verify = client.post("/v1/auth/otp/verify") {
            contentType(ContentType.Application.Json)
            setBody("""{"e164":"$e164","code":"111111","deviceName":"it"}""")
        }
        assertEquals(HttpStatusCode.OK, verify.status)
        val token = json.parseToJsonElement(verify.bodyAsText())
            .jsonObject["accessToken"]!!.jsonPrimitive.content
        val me = client.get("/v1/profile") { bearerAuth(token) }
        val accountId = json.parseToJsonElement(me.bodyAsText())
            .jsonObject["accountId"]!!.jsonPrimitive.content
        return Account(token, accountId)
    }

    @Test
    fun `block list hides presence and unblock restores it`() {
        val dataSource = DbFactory.create(
            DbConfig(postgres.jdbcUrl, postgres.username, postgres.password),
        )
        try {
            SchemaMigrator(dataSource).migrate()
            val auth = AuthServices.create(
                AuthServiceConfig(
                    jwtSecret = "block-secret-0123456789-abcdef",
                    e164Pepper = "block-pepper",
                    e164KeyBase64 = Base64.getEncoder().encodeToString(ByteArray(32) { 3 }),
                    devMode = true,
                ),
                dataSource,
            )
            val messaging = MessagingContext.create(
                dataSource = dataSource,
                redisConfig = RedisConfig(host = redis.host, port = redis.getMappedPort(6379)),
                accountRepository = auth.accountRepository,
                deviceRepository = auth.deviceRepository,
            )
            val blocks = BlockRepository(dataSource)

            testApplication {
                application { moduleWithContext(auth, messaging, blocks = blocks) }

                run {
                    val alice = register(client, "+15550000501")
                    val bob = register(client, "+15550000502")

                    // Simulate Alice being online (normally set by her WS).
                    messaging.queues.setOnline(alice.accountId, 60)

                    val before = client.get("/v1/presence/${alice.accountId}") {
                        bearerAuth(bob.token)
                    }
                    assertEquals(HttpStatusCode.OK, before.status, before.bodyAsText())
                    val beforeBody = json.parseToJsonElement(before.bodyAsText()).jsonObject
                    assertEquals(true, beforeBody["online"]!!.jsonPrimitive.content.toBoolean())

                    // Bob blocks Alice: list contains her, presence now hidden.
                    val block = client.post("/v1/blocks/${alice.accountId}") { bearerAuth(bob.token) }
                    assertEquals(HttpStatusCode.Created, block.status)

                    val list = client.get("/v1/blocks") { bearerAuth(bob.token) }
                    assertEquals(HttpStatusCode.OK, list.status)
                    val ids = json.parseToJsonElement(list.bodyAsText())
                        .jsonObject["blockedAccountIds"]!!.jsonArray.map { it.jsonPrimitive.content }
                    assertTrue(ids.contains(alice.accountId))

                    val hidden = client.get("/v1/presence/${alice.accountId}") {
                        bearerAuth(bob.token)
                    }
                    assertEquals(HttpStatusCode.OK, hidden.status)
                    val hiddenBody = json.parseToJsonElement(hidden.bodyAsText()).jsonObject
                    assertEquals(false, hiddenBody["online"]!!.jsonPrimitive.content.toBoolean())

                    // Unblock restores visibility.
                    val unblock = client.delete("/v1/blocks/${alice.accountId}") {
                        bearerAuth(bob.token)
                    }
                    assertEquals(HttpStatusCode.NoContent, unblock.status)

                    val after = client.get("/v1/presence/${alice.accountId}") {
                        bearerAuth(bob.token)
                    }
                    val afterBody = json.parseToJsonElement(after.bodyAsText()).jsonObject
                    assertEquals(true, afterBody["online"]!!.jsonPrimitive.content.toBoolean())
                }
            }
        } finally {
            dataSource.close()
        }
    }
}
