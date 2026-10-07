package com.chattlyx.server

import com.chattlyx.backend.auth.AuthServiceConfig
import com.chattlyx.backend.auth.AuthServices
import com.chattlyx.backend.common.ChattlyxServerException
import com.chattlyx.backend.db.DbConfig
import com.chattlyx.backend.db.DbFactory
import com.chattlyx.backend.db.SchemaMigrator
import com.chattlyx.backend.messaging.ConversationIds
import com.chattlyx.backend.protocol.toProtoUuid
import com.chattlyx.backend.redis.RedisConfig
import com.chattlyx.proto.Envelope
import com.chattlyx.proto.EnvelopeType
import com.chattlyx.server.messaging.MessagingContext
import com.google.protobuf.ByteString
import java.util.Base64
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Phase 2 acceptance on real infrastructure: idempotent send, seq ordering,
 * queue delivery, delete-on-ack, history sync and private discovery.
 */
@Testcontainers
class MessagingIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")

        @Container
        @JvmStatic
        val redis: GenericContainer<*> =
            GenericContainer("redis:7-alpine").withExposedPorts(6379)
    }

    private class Harness : AutoCloseable {
        val dataSource = DbFactory.create(
            DbConfig(postgres.jdbcUrl, postgres.username, postgres.password),
        )
        val auth: AuthServices
        val messaging: MessagingContext

        init {
            SchemaMigrator(dataSource).migrate()
            auth = AuthServices.create(
                AuthServiceConfig(
                    jwtSecret = "messaging-secret-0123456789-abcd",
                    e164Pepper = "messaging-pepper",
                    e164KeyBase64 = Base64.getEncoder().encodeToString(ByteArray(32) { 3 }),
                    devMode = true,
                ),
                dataSource,
            )
            messaging = MessagingContext.create(
                dataSource = dataSource,
                redisConfig = RedisConfig(host = redis.host, port = redis.getMappedPort(6379)),
                accountRepository = auth.accountRepository,
                deviceRepository = auth.deviceRepository,
            )
        }

        fun registerAccount(e164: String, name: String): UUID {
            val now = System.currentTimeMillis()
            val sha = auth.vault.discoveryHash(e164)
            val id = auth.accountRepository.createOrTouch(auth.vault.hash(e164, "messaging-pepper"), auth.vault.encrypt(e164), sha, now)
            auth.accountRepository.updateProfile(id, name, "", null, null)
            auth.deviceRepository.register(id, "test-device", now)
            return id
        }

        override fun close() {
            dataSource.close()
        }
    }

    private fun envelope(from: UUID, to: UUID, clientMessageId: UUID, text: String): Envelope =
        Envelope.newBuilder()
            .setType(EnvelopeType.ENVELOPE_TYPE_SIGNAL)
            .setSenderAccountId(from.toString())
            .setSenderDeviceId(1)
            .setCiphertext(ByteString.copyFromUtf8("cipher:$text"))
            .setClientMessageId(clientMessageId.toProtoUuid())
            .setConversationId(ConversationIds.direct(from, to))
            .build()

    @Test
    fun `send deliver ack lifecycle with idempotency`() = Harness().use { h ->
        val alice = h.registerAccount("+919800000001", "Alice")
        val bob = h.registerAccount("+919800000002", "Bob")
        val cmid = UUID.randomUUID()

        val first = h.messaging.messagingService.send(alice, 1, bob, envelope(alice, bob, cmid, "hi"))
        val retry = h.messaging.messagingService.send(alice, 1, bob, envelope(alice, bob, cmid, "hi"))
        assertEquals(first.serverMessageId, retry.serverMessageId)
        assertEquals(first.seq, retry.seq)

        val second = h.messaging.messagingService.send(
            alice,
            1,
            bob,
            envelope(alice, bob, UUID.randomUUID(), "second"),
        )
        assertTrue(second.seq > first.seq)

        val delivered = h.messaging.messagingService.drainForDelivery(bob)
        assertEquals(2, delivered.size)
        assertEquals("cipher:hi", delivered[0].ciphertext.toStringUtf8())
        assertEquals(first.serverMessageId, delivered[0].serverMessageId)
        assertEquals(first.seq, delivered[0].serverSeq)

        // Queue is drained; delete-on-ack removes the first envelope only.
        assertTrue(h.messaging.messagingService.drainForDelivery(bob).isEmpty())
        h.messaging.messagingService.ack(bob, listOf(first.serverMessageId))
        val history = h.messaging.messagingService.history(
            participant = bob,
            conversationId = ConversationIds.direct(alice, bob),
            afterSeq = 0,
            limit = 50,
        )
        assertEquals(1, history.size)
        assertEquals("cipher:second", history[0].ciphertext.toStringUtf8())
    }

    @Test
    fun `history sync rejects non participants`() = Harness().use { h ->
        val alice = h.registerAccount("+919800000011", "Alice")
        val bob = h.registerAccount("+919800000012", "Bob")
        val mallory = h.registerAccount("+919800000013", "Mallory")

        h.messaging.messagingService.send(alice, 1, bob, envelope(alice, bob, UUID.randomUUID(), "secret"))

        val conversation = ConversationIds.direct(alice, bob)
        val visible = h.messaging.messagingService.history(bob, conversation, 0, 50)
        assertEquals(1, visible.size)

        val ex = assertFailsWith<ChattlyxServerException.Forbidden> {
            h.messaging.messagingService.history(mallory, conversation, 0, 50)
        }
        assertEquals("msg/not-participant", ex.code)
    }

    @Test
    fun `self send and oversized ciphertext are rejected`() = Harness().use { h ->
        val alice = h.registerAccount("+919800000021", "Alice")

        assertFailsWith<ChattlyxServerException.Validation> {
            h.messaging.messagingService.send(alice, 1, alice, envelope(alice, alice, UUID.randomUUID(), "x"))
        }

        val huge = envelope(alice, alice, UUID.randomUUID(), "x")
        val oversized = Envelope.newBuilder(huge)
            .setCiphertext(ByteString.copyFrom(ByteArray(300_000)))
            .build()
        assertFailsWith<ChattlyxServerException.Validation> {
            h.messaging.messagingService.send(alice, 1, UUID.randomUUID(), oversized)
        }
    }

    @Test
    fun `private discovery returns public fields only`() = Harness().use { h ->
        h.registerAccount("+919800000031", "Asha")
        h.registerAccount("+919800000032", "Vikram")

        val hashes = listOf(
            h.auth.vault.discoveryHash("+919800000031"),
            h.auth.vault.discoveryHash("+919800000099"), // not registered
        )
        val matches = h.messaging.contactsService.discover(hashes)

        assertEquals(1, matches.size)
        assertEquals("Asha", matches[0].displayName)
        assertNull(matches[0].username)
    }

    @Test
    fun `discovery validates hash shape and batch size`() = Harness().use { h ->
        assertFailsWith<ChattlyxServerException.Validation> {
            h.messaging.contactsService.discover(listOf("not-a-hash"))
        }
        assertFailsWith<ChattlyxServerException.Validation> {
            h.messaging.contactsService.discover(List(1001) { "a".repeat(64) })
        }
    }
}
