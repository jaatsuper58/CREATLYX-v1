package com.chattlyx.server

import com.chattlyx.backend.auth.AuthServiceConfig
import com.chattlyx.backend.auth.AuthServices
import com.chattlyx.backend.db.DbConfig
import com.chattlyx.backend.db.DbFactory
import com.chattlyx.backend.db.SchemaMigrator
import com.chattlyx.server.groups.GroupContext
import com.chattlyx.server.messaging.ConnectionRegistry
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
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
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Phase 4 acceptance (GRP-*): create/list/get/rename/add/remove with
 * member-only reads, admin-only mutations and no existence leak to
 * strangers — end to end on real Postgres.
 */
@Testcontainers
class GroupIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun startApp(block: suspend (io.ktor.client.HttpClient) -> Unit) {
        val dataSource = DbFactory.create(
            DbConfig(postgres.jdbcUrl, postgres.username, postgres.password),
        )
        try {
            SchemaMigrator(dataSource).migrate()
            val auth = AuthServices.create(
                AuthServiceConfig(
                    jwtSecret = "group-secret-0123456789-abcdef",
                    e164Pepper = "group-pepper",
                    e164KeyBase64 = Base64.getEncoder().encodeToString(ByteArray(32) { 5 }),
                    devMode = true,
                ),
                dataSource,
            )
            val groups = GroupContext.create(
                dataSource = dataSource,
                accountRepository = auth.accountRepository,
                registry = ConnectionRegistry(),
            )

            testApplication {
                application { moduleWithContext(auth, groups = groups) }
                block(client)
            }
        } finally {
            dataSource.close()
        }
    }

    private data class Account(val token: String, val accountId: String)

    private suspend fun register(client: io.ktor.client.HttpClient, e164: String): Account {
        val request = client.post("/v1/auth/otp/request") {
            contentType(ContentType.Application.Json)
            setBody("""{"e164":"$e164"}""")
        }
        assertEquals(HttpStatusCode.OK, request.status)
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

    private fun parseGroupId(body: String): String =
        json.parseToJsonElement(body).jsonObject["groupId"]!!.jsonPrimitive.content

    @Test
    fun `create list and get enforce membership`() = startApp { client ->
        val alice = register(client, "+15550000101")
        val bob = register(client, "+15550000102")
        val mallory = register(client, "+15550000103")

        val created = client.post("/v1/groups") {
            bearerAuth(alice.token)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Weekend crew","memberAccountIds":["${bob.accountId}"]}""")
        }
        assertEquals(HttpStatusCode.Created, created.status, created.bodyAsText())
        val groupId = parseGroupId(created.bodyAsText())
        val createdBody = json.parseToJsonElement(created.bodyAsText()).jsonObject
        assertEquals(2, createdBody["members"]!!.jsonArray.size)

        // Alice sees it in her list; Mallory's list stays empty.
        val aliceList = client.get("/v1/groups") { bearerAuth(alice.token) }
        assertEquals(HttpStatusCode.OK, aliceList.status)
        assertTrue(aliceList.bodyAsText().contains(groupId))
        val malloryList = client.get("/v1/groups") { bearerAuth(mallory.token) }
        assertTrue(!malloryList.bodyAsText().contains(groupId))

        // Member read ok; stranger read is a 404 with no existence leak.
        val bobGet = client.get("/v1/groups/$groupId") { bearerAuth(bob.token) }
        assertEquals(HttpStatusCode.OK, bobGet.status)
        val strangerGet = client.get("/v1/groups/$groupId") { bearerAuth(mallory.token) }
        assertEquals(HttpStatusCode.NotFound, strangerGet.status)
    }

    @Test
    fun `rename requires admin and bumps membership version`() = startApp { client ->
        val alice = register(client, "+15550000201")
        val bob = register(client, "+15550000202")

        val created = client.post("/v1/groups") {
            bearerAuth(alice.token)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Old name","memberAccountIds":["${bob.accountId}"]}""")
        }
        val groupId = parseGroupId(created.bodyAsText())
        val versionBefore = json.parseToJsonElement(created.bodyAsText())
            .jsonObject["membershipVersion"]!!.jsonPrimitive.content.toLong()

        // Bob is a plain member: rename is forbidden.
        val forbidden = client.patch("/v1/groups/$groupId") {
            bearerAuth(bob.token)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Hijacked"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, forbidden.status)

        val renamed = client.patch("/v1/groups/$groupId") {
            bearerAuth(alice.token)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"New name"}""")
        }
        assertEquals(HttpStatusCode.OK, renamed.status, renamed.bodyAsText())
        val body = json.parseToJsonElement(renamed.bodyAsText()).jsonObject
        assertEquals("New name", body["name"]!!.jsonPrimitive.content)
        val versionAfter = body["membershipVersion"]!!.jsonPrimitive.content.toLong()
        assertTrue(versionAfter > versionBefore)
    }

    @Test
    fun `members can be added and removed with role rules`() = startApp { client ->
        val alice = register(client, "+15550000301")
        val bob = register(client, "+15550000302")
        val carol = register(client, "+15550000303")

        val created = client.post("/v1/groups") {
            bearerAuth(alice.token)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Ops","memberAccountIds":["${bob.accountId}"]}""")
        }
        val groupId = parseGroupId(created.bodyAsText())

        // Bob (member) cannot add Carol; Alice (owner) can.
        val bobAdd = client.post("/v1/groups/$groupId/members") {
            bearerAuth(bob.token)
            contentType(ContentType.Application.Json)
            setBody("""{"accountIds":["${carol.accountId}"]}""")
        }
        assertEquals(HttpStatusCode.Forbidden, bobAdd.status)

        val aliceAdd = client.post("/v1/groups/$groupId/members") {
            bearerAuth(alice.token)
            contentType(ContentType.Application.Json)
            setBody("""{"accountIds":["${carol.accountId}"]}""")
        }
        assertEquals(HttpStatusCode.Created, aliceAdd.status, aliceAdd.bodyAsText())
        assertEquals(3, json.parseToJsonElement(aliceAdd.bodyAsText())
            .jsonObject["members"]!!.jsonArray.size)

        // Carol can leave by deleting herself; she loses access afterwards.
        val leave = client.delete("/v1/groups/$groupId/members/${carol.accountId}") {
            bearerAuth(carol.token)
        }
        assertEquals(HttpStatusCode.OK, leave.status, leave.bodyAsText())
        val carolGet = client.get("/v1/groups/$groupId") { bearerAuth(carol.token) }
        assertEquals(HttpStatusCode.NotFound, carolGet.status)

        // Unknown accounts are rejected at add time.
        val bogusAdd = client.post("/v1/groups/$groupId/members") {
            bearerAuth(alice.token)
            contentType(ContentType.Application.Json)
            setBody("""{"accountIds":["00000000-0000-0000-0000-000000000000"]}""")
        }
        assertEquals(HttpStatusCode.NotFound, bogusAdd.status)
    }

    @Test
    fun `owner leaving promotes the earliest member`() = startApp { client ->
        val alice = register(client, "+15550000401")
        val bob = register(client, "+15550000402")

        val created = client.post("/v1/groups") {
            bearerAuth(alice.token)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Duo","memberAccountIds":["${bob.accountId}"]}""")
        }
        val groupId = parseGroupId(created.bodyAsText())

        val leave = client.delete("/v1/groups/$groupId/members/${alice.accountId}") {
            bearerAuth(alice.token)
        }
        assertEquals(HttpStatusCode.OK, leave.status, leave.bodyAsText())

        val bobView = client.get("/v1/groups/$groupId") { bearerAuth(bob.token) }
        assertEquals(HttpStatusCode.OK, bobView.status)
        val members = json.parseToJsonElement(bobView.bodyAsText())
            .jsonObject["members"]!!.jsonArray
        assertEquals(1, members.size)
        assertEquals("owner", members[0].jsonObject["role"]!!.jsonPrimitive.content)
    }
}
