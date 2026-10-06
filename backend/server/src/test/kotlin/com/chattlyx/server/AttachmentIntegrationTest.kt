package com.chattlyx.server

import com.chattlyx.backend.auth.AuthServiceConfig
import com.chattlyx.backend.auth.AuthServices
import com.chattlyx.backend.db.DbConfig
import com.chattlyx.backend.db.DbFactory
import com.chattlyx.backend.db.SchemaMigrator
import com.chattlyx.backend.storage.FileSystemBlobStore
import com.chattlyx.backend.storage.StorageConfig
import com.chattlyx.server.attachments.AttachmentContext
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
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
 * Phase 3 acceptance (MED-*): declare -> upload ciphertext -> metadata ->
 * download, with sender/recipient access control, size validation and
 * not-yet-uploaded handling — end to end on real Postgres.
 */
@Testcontainers
class AttachmentIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val hex = java.util.HexFormat.of()

    private fun startApp(
        wireGroups: Boolean = false,
        block: suspend (io.ktor.client.HttpClient, com.chattlyx.backend.db.GroupRepository?) -> Unit,
    ) {
        val dataSource = DbFactory.create(
            DbConfig(postgres.jdbcUrl, postgres.username, postgres.password),
        )
        val blobDir = Files.createTempDirectory("chattlyx-attachments-it")
        try {
            SchemaMigrator(dataSource).migrate()
            val auth = AuthServices.create(
                AuthServiceConfig(
                    jwtSecret = "attachment-secret-0123456789-abcd",
                    e164Pepper = "attachment-pepper",
                    e164KeyBase64 = Base64.getEncoder().encodeToString(ByteArray(32) { 7 }),
                    devMode = true,
                ),
                dataSource,
            )
            val groupRepository = if (wireGroups) {
                com.chattlyx.backend.db.GroupRepository(dataSource)
            } else {
                null
            }
            val attachments = AttachmentContext.create(
                dataSource = dataSource,
                storageConfig = StorageConfig(
                    rootDir = blobDir.toString(),
                    maxAttachmentBytes = 1_000_000,
                ),
                blobStore = FileSystemBlobStore(blobDir),
                groupMembership = groupRepository?.let { repo ->
                    { conversationId: String, accountId: java.util.UUID ->
                        val groupId = runCatching {
                            java.util.UUID.fromString(conversationId.removePrefix("grp:"))
                        }.getOrNull()
                        groupId != null && repo.memberIds(groupId).contains(accountId)
                    }
                },
            )

            testApplication {
                application { moduleWithContext(auth, attachments = attachments) }
                block(client, groupRepository)
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
            setBody("""{"e164":"$e164","code":"111111","deviceName":"Attachment device"}""")
        }
        assertEquals(HttpStatusCode.OK, verify.status)
        val token = json.parseToJsonElement(verify.bodyAsText())
            .jsonObject["accessToken"]!!.jsonPrimitive.content

        val me = client.get("/v1/profile") { bearerAuth(token) }
        val accountId = json.parseToJsonElement(me.bodyAsText())
            .jsonObject["accountId"]!!.jsonPrimitive.content
        return Account(token, accountId)
    }

    private suspend fun declare(
        client: io.ktor.client.HttpClient,
        token: String,
        sizeBytes: Long,
        sha256Hex: String,
        recipientAccountId: String? = null,
        kind: String = "image",
    ): HttpStatusCode {
        val recipientField = recipientAccountId?.let { ""","recipientAccountId":"$it"""" }.orEmpty()
        val response = client.post("/v1/attachments") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(
                """{"kind":"$kind","mimeType":"image/jpeg","sizeBytes":$sizeBytes,""" +
                    """"sha256Hex":"$sha256Hex"$recipientField}""",
            )
        }
        return response.status
    }

    @Test
    fun `declare upload meta download roundtrip`() = startApp { client, _ ->
        val alice = register(client, "+919800000101")
        val ciphertext = ByteArray(2048) { (it % 256).toByte() }
        val sha = hex.formatHex(MessageDigest.getInstance("SHA-256").digest(ciphertext))

        val declared = client.post("/v1/attachments") {
            bearerAuth(alice.token)
            contentType(ContentType.Application.Json)
            setBody(
                """{"kind":"image","mimeType":"image/jpeg","sizeBytes":${ciphertext.size},""" +
                    """"sha256Hex":"$sha","width":640,"height":480}""",
            )
        }
        assertEquals(HttpStatusCode.Created, declared.status, declared.bodyAsText())
        val declaredObj = json.parseToJsonElement(declared.bodyAsText()).jsonObject
        val attachmentId = assertNotNull(declaredObj["attachmentId"]).jsonPrimitive.content

        val upload = client.put("/v1/attachments/$attachmentId/data") {
            bearerAuth(alice.token)
            contentType(ContentType.Application.OctetStream)
            setBody(ciphertext)
        }
        assertEquals(HttpStatusCode.NoContent, upload.status)

        val meta = client.get("/v1/attachments/$attachmentId") { bearerAuth(alice.token) }
        assertEquals(HttpStatusCode.OK, meta.status)
        val metaText = meta.bodyAsText()
        assertTrue(metaText.contains("\"status\":\"ready\""))
        assertTrue(metaText.contains("\"downloadUrl\":\"/v1/attachments/$attachmentId/data\""))
        assertTrue(metaText.contains("\"sha256Hex\":\"$sha\""))

        val download = client.get("/v1/attachments/$attachmentId/data") { bearerAuth(alice.token) }
        assertEquals(HttpStatusCode.OK, download.status)
        assertContentEquals(ciphertext, download.bodyAsBytes())
    }

    @Test
    fun `recipient downloads, strangers get 404`() = startApp { client, _ ->
        val alice = register(client, "+919800000102")
        val bob = register(client, "+919800000103")
        val carol = register(client, "+919800000104")

        val ciphertext = "MED-04 ciphertext".toByteArray()
        val sha = hex.formatHex(MessageDigest.getInstance("SHA-256").digest(ciphertext))

        val declared = client.post("/v1/attachments") {
            bearerAuth(alice.token)
            contentType(ContentType.Application.Json)
            setBody(
                """{"kind":"file","mimeType":"application/pdf","sizeBytes":${ciphertext.size},""" +
                    """"sha256Hex":"$sha","recipientAccountId":"${bob.accountId}",""" +
                    """"fileName":"contract.pdf"}""",
            )
        }
        assertEquals(HttpStatusCode.Created, declared.status, declared.bodyAsText())
        val attachmentId = json.parseToJsonElement(declared.bodyAsText())
            .jsonObject["attachmentId"]!!.jsonPrimitive.content

        client.put("/v1/attachments/$attachmentId/data") {
            bearerAuth(alice.token)
            contentType(ContentType.Application.OctetStream)
            setBody(ciphertext)
        }

        val bobMeta = client.get("/v1/attachments/$attachmentId") { bearerAuth(bob.token) }
        assertEquals(HttpStatusCode.OK, bobMeta.status)
        val bobData = client.get("/v1/attachments/$attachmentId/data") { bearerAuth(bob.token) }
        assertContentEquals(ciphertext, bobData.bodyAsBytes())

        // Existence is not leaked to strangers.
        assertEquals(
            HttpStatusCode.NotFound,
            client.get("/v1/attachments/$attachmentId") { bearerAuth(carol.token) }.status,
        )
        assertEquals(
            HttpStatusCode.NotFound,
            client.get("/v1/attachments/$attachmentId/data") { bearerAuth(carol.token) }.status,
        )
    }

    @Test
    fun `validation and access failures`() = startApp { client, _ ->
        val alice = register(client, "+919800000105")
        val mallory = register(client, "+919800000106")
        val sha = hex.formatHex(MessageDigest.getInstance("SHA-256").digest(byteArrayOf(1, 2, 3)))

        // Declared size above the configured limit is rejected.
        assertEquals(
            HttpStatusCode.BadRequest,
            declare(client, alice.token, sizeBytes = 2_000_000, sha256Hex = sha),
        )
        // Malformed sha hex is rejected.
        assertEquals(
            HttpStatusCode.BadRequest,
            declare(client, alice.token, sizeBytes = 3, sha256Hex = "zz"),
        )
        // Unknown kind is rejected.
        val badKind = client.post("/v1/attachments") {
            bearerAuth(alice.token)
            contentType(ContentType.Application.Json)
            setBody("""{"kind":"hologram","mimeType":"x/y","sizeBytes":3,"sha256Hex":"$sha"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, badKind.status)

        val declared = client.post("/v1/attachments") {
            bearerAuth(alice.token)
            contentType(ContentType.Application.Json)
            setBody(
                """{"kind":"voice","mimeType":"audio/opus","sizeBytes":3,"sha256Hex":"$sha",""" +
                    """"durationMs":1500}""",
            )
        }
        assertEquals(HttpStatusCode.Created, declared.status, declared.bodyAsText())
        val attachmentId = json.parseToJsonElement(declared.bodyAsText())
            .jsonObject["attachmentId"]!!.jsonPrimitive.content

        // Download before upload: 404 for the sender too.
        assertEquals(
            HttpStatusCode.NotFound,
            client.get("/v1/attachments/$attachmentId/data") { bearerAuth(alice.token) }.status,
        )
        // Only the declared sender may upload.
        assertEquals(
            HttpStatusCode.Forbidden,
            client.put("/v1/attachments/$attachmentId/data") {
                bearerAuth(mallory.token)
                contentType(ContentType.Application.OctetStream)
                setBody(byteArrayOf(1, 2, 3))
            }.status,
        )
        // Size mismatch is rejected.
        assertEquals(
            HttpStatusCode.BadRequest,
            client.put("/v1/attachments/$attachmentId/data") {
                bearerAuth(alice.token)
                contentType(ContentType.Application.OctetStream)
                setBody(byteArrayOf(1, 2, 3, 4))
            }.status,
        )
        // Correct upload then double-upload conflicts.
        assertEquals(
            HttpStatusCode.NoContent,
            client.put("/v1/attachments/$attachmentId/data") {
                bearerAuth(alice.token)
                contentType(ContentType.Application.OctetStream)
                setBody(byteArrayOf(1, 2, 3))
            }.status,
        )
        assertEquals(
            HttpStatusCode.Conflict,
            client.put("/v1/attachments/$attachmentId/data") {
                bearerAuth(alice.token)
                contentType(ContentType.Application.OctetStream)
                setBody(byteArrayOf(1, 2, 3))
            }.status,
        )
    }

    @Test
    fun `group blob readable by members only`() = startApp(wireGroups = true) { client, groupRepository ->
        val repo = assertNotNull(groupRepository)
        val alice = register(client, "+919800000121")
        val bob = register(client, "+919800000122")
        val carol = register(client, "+919800000123")

        val aliceId = java.util.UUID.fromString(alice.accountId)
        val bobId = java.util.UUID.fromString(bob.accountId)
        val groupId = java.util.UUID.randomUUID()
        repo.insertGroup(
            com.chattlyx.backend.db.GroupRow(
                id = groupId,
                name = "Media group",
                createdBy = aliceId,
                createdAt = System.currentTimeMillis(),
                membershipVersion = 1L,
            ),
        )
        repo.insertMembers(
            groupId,
            listOf(aliceId, bobId),
            role = "member",
            addedBy = aliceId,
        )
        val conversationId = "grp:$groupId"

        val ciphertext = ByteArray(1024) { (it % 256).toByte() }
        val sha = hex.formatHex(MessageDigest.getInstance("SHA-256").digest(ciphertext))

        // Sender (member) declares against the group conversation.
        val declared = client.post("/v1/attachments") {
            bearerAuth(alice.token)
            contentType(ContentType.Application.Json)
            setBody(
                """{"kind":"image","mimeType":"image/jpeg","sizeBytes":${ciphertext.size},""" +
                    """"sha256Hex":"$sha","conversationId":"$conversationId"}""",
            )
        }
        assertEquals(HttpStatusCode.Created, declared.status, declared.bodyAsText())
        val attachmentId = assertNotNull(
            json.parseToJsonElement(declared.bodyAsText()).jsonObject["attachmentId"],
        ).jsonPrimitive.content

        client.put("/v1/attachments/$attachmentId/data") {
            bearerAuth(alice.token)
            contentType(ContentType.Application.OctetStream)
            setBody(ciphertext)
        }

        // Another member reads meta + data.
        assertEquals(
            HttpStatusCode.OK,
            client.get("/v1/attachments/$attachmentId") { bearerAuth(bob.token) }.status,
        )
        val bobData = client.get("/v1/attachments/$attachmentId/data") { bearerAuth(bob.token) }
        assertEquals(HttpStatusCode.OK, bobData.status)
        assertContentEquals(ciphertext, bobData.bodyAsBytes())

        // Non-members learn nothing about the blob.
        assertEquals(
            HttpStatusCode.NotFound,
            client.get("/v1/attachments/$attachmentId") { bearerAuth(carol.token) }.status,
        )
        assertEquals(
            HttpStatusCode.NotFound,
            client.get("/v1/attachments/$attachmentId/data") { bearerAuth(carol.token) }.status,
        )

        // Non-members may not declare into the group conversation either.
        val carolDeclare = client.post("/v1/attachments") {
            bearerAuth(carol.token)
            contentType(ContentType.Application.Json)
            setBody(
                """{"kind":"image","mimeType":"image/jpeg","sizeBytes":4,""" +
                    """"sha256Hex":"$sha","conversationId":"$conversationId"}""",
            )
        }
        assertEquals(HttpStatusCode.Forbidden, carolDeclare.status)
    }
}
