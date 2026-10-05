package com.chattlyx.data.messaging

import com.chattlyx.core.common.dispatchers.ChattlyxDispatcher
import com.chattlyx.core.common.dispatchers.Dispatcher
import com.chattlyx.core.network.RealtimeClient
import com.chattlyx.core.network.RealtimeConnectionState
import com.chattlyx.data.auth.SecureTokenStore
import com.chattlyx.domain.messaging.RealtimeEvents
import com.chattlyx.domain.messaging.TypingEvent
import com.chattlyx.proto.Frame
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Owns the realtime session lifecycle after registration (MSG-07): connects
 * with stored credentials, drains incoming frames into the message pipeline,
 * and re-syncs on every reconnect. Started once by the app when the gate
 * opens; stop() is called on sign out.
 */
@Singleton
class RealtimeCoordinator @Inject constructor(
    private val realtime: RealtimeClient,
    private val tokenStore: SecureTokenStore,
    private val messageRepository: MessageRepositoryImpl,
    private val conversationRepository: ConversationRepositoryImpl,
    private val syncEngine: SyncEngine,
    private val groupRepository: com.chattlyx.data.groups.GroupRepositoryImpl,
    @Dispatcher(ChattlyxDispatcher.IO) private val dispatcher: CoroutineDispatcher,
) : RealtimeEvents {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _typingEvents = MutableSharedFlow<TypingEvent>(extraBufferCapacity = 16)
    override val typingEvents: SharedFlow<TypingEvent> = _typingEvents.asSharedFlow()

    @Volatile
    private var started = false

    /** Idempotent; call whenever a session exists. */
    fun start() {
        if (started) return
        started = true

        scope.launch {
            val token = tokenStore.accessToken() ?: return@launch
            val deviceId = tokenStore.deviceId() ?: return@launch

            collectFrames()
            realtime.connect(token, deviceId)
        }
    }

    suspend fun stop() {
        started = false
        realtime.disconnect()
    }

    /** Push wake / network change hook. */
    fun wake() {
        if (!started) return
        realtime.requestReconnect()
        scope.launch { syncEngine.syncAfterReconnect() }
    }

    private fun collectFrames() {
        scope.launch {
            realtime.incomingFrames.collect { frame -> handleFrame(frame) }
        }
        scope.launch {
            realtime.connectionState.collect { state ->
                if (state == RealtimeConnectionState.CONNECTED) {
                    syncEngine.syncAfterReconnect()
                }
            }
        }
    }

    private suspend fun handleFrame(frame: Frame) {
        when {
            frame.hasGroupUpdate() -> {
                // GRP-*: membership/name changed; refresh the affected group.
                val groupId = frame.groupUpdate.groupId
                if (groupId.isNotEmpty()) {
                    groupRepository.refreshGroup(groupId)
                }
            }
            frame.hasDeliver() -> {
                val envelope = frame.deliver.envelope
                val incoming = messageRepository.onDeliver(envelope)
                if (incoming != null) {
                    conversationRepository.onIncomingMessage(incoming, incrementUnread = true)
                    messageRepository.acknowledge(listOfNotNull(incoming.serverId.takeIf { it.isNotEmpty() }))
                } else if (envelope.serverMessageId.isNotEmpty()) {
                    // Receipts/typing inside the session still need acking.
                    messageRepository.acknowledge(listOf(envelope.serverMessageId))
                }

                // Typing surfaces through decrypted content; plaintext typing
                // frames (server-routed) arrive as TYPING envelopes with a flag.
                if (envelope.type == com.chattlyx.proto.EnvelopeType.ENVELOPE_TYPE_TYPING) {
                    val started = envelope.ciphertext.size() > 0 && envelope.ciphertext.byteAt(0).toInt() == 1
                    _typingEvents.tryEmit(
                        TypingEvent(
                            conversationId = envelope.conversationId,
                            senderAccountId = envelope.senderAccountId,
                            started = started,
                        ),
                    )
                }
            }

            frame.hasAck() -> messageRepository.onServerAck(frame.ack)

            frame.hasError() -> {
                Timber.w("Realtime error frame code=%s", frame.error.code)
                if (frame.error.code == "auth/token-invalid" || frame.error.code == "auth/expired") {
                    // Access token died mid-session: refresh will run on next REST call.
                    realtime.requestReconnect()
                }
            }

            else -> Unit // pong / presence handled by heartbeat policy
        }
    }
}
