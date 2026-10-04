package com.chattlyx.server

import com.chattlyx.backend.auth.AuthServiceConfig
import com.chattlyx.backend.auth.AuthServices
import com.chattlyx.backend.db.DbConfig
import com.chattlyx.backend.db.DbFactory
import com.chattlyx.backend.db.SchemaMigrator
import com.chattlyx.backend.messaging.ConversationIds
import com.chattlyx.backend.protocol.toProtoUuid
import com.chattlyx.backend.redis.RedisConfig
import com.chattlyx.proto.AuthFrame
import com.chattlyx.proto.Envelope
import com.chattlyx.proto.EnvelopeType
import com.chattlyx.proto.Frame as ProtoFrame
import com.chattlyx.server.messaging.MessagingContext
import com.google.protobuf.ByteString
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import java.util.Base64
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/** End-to-end WebSocket pipeline: auth, live delivery, ack, receipts. */
@Testcontainers
class WsGatewayIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")

        @Container
        @JvmStatic
        val redis: GenericContainer<*> =
            GenericContainer("redis:7-alpine").withExposedPorts(6379)
    }

    @Test
    fun `alice sends, bob receives live, ack removes envelope`() {
        val dataSource = DbFactory.create(
            DbConfig(postgres.jdbcUrl, postgres.username, postgres.password),
        )
        try {
            SchemaMigrator(dataSource).migrate()
            val auth = AuthServices.create(
                AuthServiceConfig(
                    jwtSecret = "ws-secret-0123456789-abcdef-0000",
                    e164Pepper = "ws-pepper",
                    e164KeyBase64 = Base64.getEncoder().encodeToString(ByteArray(32) { 5 }),
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

            val now = System.currentTimeMillis()
            val alice = auth.accountRepository.createOrTouch(
                auth.vault.hash("+919811111111", "ws-pepper"),
                auth.vault.encrypt("+919811111111"),
                auth.vault.discoveryHash("+919811111111"),
                now,
            )
            val bob = auth.accountRepository.createOrTouch(
                auth.vault.hash("+919822222222", "ws-pepper"),
                auth.vault.encrypt("+919822222222"),
                auth.vault.discoveryHash("+919822222222"),
                now,
            )
            val aliceDevice = auth.deviceRepository.register(alice, "Alice phone", now)
            val bobDevice = auth.deviceRepository.register(bob, "Bob phone", now)

            val aliceToken = auth.tokenService.issue(alice, aliceDevice).accessToken
            val bobToken = auth.tokenService.issue(bob, bobDevice).accessToken

            testApplication {
                application { moduleWithContext(auth, messaging) }

                val wsClient = createClient { install(WebSockets) }

                wsClient.webSocket("/v1/ws") {
                    // Bob connects and idles.
                    send(Frame.Binary(true, authFrame(bobToken, bobDevice)))

                    val aliceClient = createClient { install(WebSockets) }
                    val delivery = launch {
                        val raw = withTimeout(5_000) { incoming.receive() }
                        val frame = ProtoFrame.parseFrom((raw as Frame.Binary).readBytes())
                        assertTrue(frame.hasDeliver())
                        assertEquals(alice.toString(), frame.deliver.envelope.senderAccountId)
                        assertEquals("cipher:hello bob", frame.deliver.envelope.ciphertext.toStringUtf8())

                        // ACK the delivery.
                        send(
                            Frame.Binary(
                                true,
                                ProtoFrame.newBuilder()
                                    .setAck(
                                        com.chattlyx.proto.AckFrame.newBuilder()
                                            .setServerMessageId(frame.deliver.envelope.serverMessageId),
                                    )
                                    .build()
                                    .toByteArray(),
                            ),
                        )
                    }

                    aliceClient.webSocket("/v1/ws") {
                        send(Frame.Binary(true, authFrame(aliceToken, aliceDevice)))

                        val cmid = UUID.randomUUID()
                        val envelope = Envelope.newBuilder()
                            .setType(EnvelopeType.ENVELOPE_TYPE_SIGNAL)
                            .setSenderAccountId(alice.toString())
                            .setSenderDeviceId(aliceDevice.toInt())
                            .setCiphertext(ByteString.copyFromUtf8("cipher:hello bob"))
                            .setClientMessageId(cmid.toProtoUuid())
                            .setConversationId(ConversationIds.direct(alice, bob))
                            .build()
                        send(
                            Frame.Binary(
                                true,
                                ProtoFrame.newBuilder()
                                    .setSend(
                                        com.chattlyx.proto.SendFrame.newBuilder()
                                            .setEnvelope(envelope)
                                            .setRecipientAccountId(bob.toString()),
                                    )
                                    .build()
                                    .toByteArray(),
                            ),
                        )

                        // Sender gets the server ack with seq + timestamp.
                        val raw = withTimeout(5_000) { incoming.receive() }
                        val frame = ProtoFrame.parseFrom((raw as Frame.Binary).readBytes())
                        assertTrue(frame.hasAck())
                        assertEquals(cmid, frame.ack.clientMessageId.toJavaUuid())
                        assertTrue(frame.ack.serverTimestampMs > 0)
                    }

                    withTimeout(5_000) { delivery.join() }

                    // After Bob's ack, history for the conversation is empty.
                    val history = messaging.messagingService.history(
                        participant = bob,
                        conversationId = ConversationIds.direct(alice, bob),
                        afterSeq = 0,
                        limit = 50,
                    )
                    assertTrue(history.isEmpty())
                }
            }
        } finally {
            dataSource.close()
        }
    }

    private fun authFrame(token: String, deviceId: Long): ByteArray =
        ProtoFrame.newBuilder()
            .setAuth(AuthFrame.newBuilder().setAccessToken(token).setDeviceId(deviceId.toInt()))
            .build()
            .toByteArray()

    private fun com.chattlyx.proto.Uuid.toJavaUuid(): UUID =
        UUID(mostSignificantBits, leastSignificantBits)
}
