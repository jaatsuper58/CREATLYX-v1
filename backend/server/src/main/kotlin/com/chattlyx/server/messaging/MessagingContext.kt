package com.chattlyx.server.messaging

import com.chattlyx.backend.db.AccountRepository
import com.chattlyx.backend.db.DeviceRepository
import com.chattlyx.backend.db.EnvelopeRepository
import com.chattlyx.backend.messaging.ContactsService
import com.chattlyx.backend.messaging.FakePushGateway
import com.chattlyx.backend.messaging.MessagingService
import com.chattlyx.backend.messaging.PeerNotifier
import com.chattlyx.backend.messaging.PushTokenLookup
import com.chattlyx.backend.redis.DeliveryQueues
import com.chattlyx.backend.redis.RedisConfig
import com.chattlyx.backend.redis.RedisFactory
import io.ktor.server.websocket.DefaultWebSocketServerSession
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.runBlocking

/**
 * Phase 2 wiring: envelope store, delivery queues, live connection registry
 * and the services that bind them. The registry doubles as [PeerNotifier].
 */
class ConnectionRegistry : PeerNotifier {

    private val connections = ConcurrentHashMap<UUID, MutableSet<DefaultWebSocketServerSession>>()

    fun add(accountId: UUID, session: DefaultWebSocketServerSession) {
        connections.computeIfAbsent(accountId) { ConcurrentHashMap.newKeySet() } += session
    }

    fun remove(accountId: UUID, session: DefaultWebSocketServerSession) {
        connections[accountId]?.remove(session)
    }

    override fun isLive(accountId: UUID): Boolean =
        connections[accountId]?.isNotEmpty() == true

    /**
     * Delivery hook installed by the WS gateway after construction (avoids a
     * registry <-> MessagingService constructor cycle). Invoked per session.
     */
    lateinit var deliverTo: (accountId: UUID, session: DefaultWebSocketServerSession) -> Unit

    override fun notifyEnvelope(accountId: UUID) {
        val sessions = connections[accountId] ?: return
        sessions.forEach { session ->
            runCatching { runBlocking { deliverTo(accountId, session) } }
        }
    }

    fun activeSessionCount(accountId: UUID): Int = connections[accountId]?.size ?: 0

    /** Ephemeral frame broadcast to every live session of an account. */
    fun broadcast(accountId: UUID, frame: io.ktor.websocket.Frame) {
        connections[accountId]?.forEach { session ->
            runCatching { runBlocking { session.send(frame) } }
        }
    }
}

class MessagingContext(
    val messagingService: MessagingService,
    val contactsService: ContactsService,
    val registry: ConnectionRegistry,
    val queues: DeliveryQueues,
) {
    companion object {

        fun create(
            dataSource: javax.sql.DataSource,
            redisConfig: RedisConfig,
            accountRepository: AccountRepository,
            deviceRepository: DeviceRepository,
        ): MessagingContext {
            val registry = ConnectionRegistry()
            val queues = DeliveryQueues(RedisFactory.create(redisConfig))
            val envelopes = EnvelopeRepository(dataSource)

            val tokenLookup = object : PushTokenLookup {
                override fun pushTokensFor(accountId: UUID): List<Pair<Long, String>> =
                    deviceRepository.pushTokensForAccount(accountId)
            }

            return MessagingContext(
                messagingService = MessagingService(
                    envelopes = envelopes,
                    queues = queues,
                    notifier = registry,
                    push = FakePushGateway(), // production: FCM HTTP v1 sender
                    tokenLookup = tokenLookup,
                ),
                contactsService = ContactsService(accountRepository),
                registry = registry,
                queues = queues,
            )
        }
    }
}
