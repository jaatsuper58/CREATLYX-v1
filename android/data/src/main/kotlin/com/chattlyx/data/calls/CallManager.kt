package com.chattlyx.data.calls

import android.content.Context
import com.chattlyx.core.common.dispatchers.ChattlyxDispatcher
import com.chattlyx.core.common.dispatchers.Dispatcher
import com.chattlyx.core.common.error.ChattlyError
import com.chattlyx.core.common.id.UuidV7
import com.chattlyx.core.common.result.Result
import com.chattlyx.core.database.dao.CallLogDao
import com.chattlyx.core.database.entity.CallLogEntity
import com.chattlyx.core.network.RealtimeClient
import com.chattlyx.core.rtc.CallEngine
import com.chattlyx.core.rtc.CallInfo
import com.chattlyx.core.rtc.CallMediaType
import com.chattlyx.core.rtc.CallState
import com.chattlyx.core.rtc.IceCandidate
import com.chattlyx.core.rtc.LocalSdp
import com.chattlyx.core.rtc.SdpType
import com.chattlyx.core.rtc.WebRtcCallEngine
import com.chattlyx.data.auth.SecureTokenStore
import com.chattlyx.data.messaging.IdentityKeyStore
import com.chattlyx.data.messaging.PeerKeyResolver
import com.chattlyx.data.messaging.SessionCipher
import com.chattlyx.domain.calls.CallDirection
import com.chattlyx.domain.calls.CallHistoryRepository
import com.chattlyx.domain.calls.CallLogEntry
import com.chattlyx.domain.calls.CallMedia
import com.chattlyx.domain.calls.CallSession
import com.chattlyx.domain.calls.CallSessionState
import com.chattlyx.domain.calls.CallSignal
import com.chattlyx.domain.calls.CallSignalKind
import com.chattlyx.proto.CallSignalContent
import com.chattlyx.proto.CallSignalFrame
import com.chattlyx.proto.Frame
import com.google.protobuf.ByteString
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * CALL-01/02/04/05 state machine: E2EE signalling over the shared WebSocket
 * plus the WebRTC engine lifecycle. All SDP/ICE payloads travel encrypted
 * with the session cipher (zero-knowledge server relay).
 */
@Singleton
class CallManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rtcEngine: WebRtcCallEngine,
    private val realtime: RealtimeClient,
    private val identityKeyStore: IdentityKeyStore,
    private val peerKeyResolver: PeerKeyResolver,
    private val tokenStore: SecureTokenStore,
    private val callLogDao: CallLogDao,
    private val callNotifications: CallNotifications,
    @Dispatcher(ChattlyxDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : CallSession, CallHistoryRepository {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val cipher: SessionCipher by lazy { SessionCipher(identityKeyStore) }

    private val _state = MutableStateFlow<CallSessionState>(CallSessionState.Idle)
    override val state: Flow<CallSessionState> = _state.asStateFlow()

    private val _muted = MutableStateFlow(false)
    override val muted: Flow<Boolean> = _muted.asStateFlow()

    private val _videoEnabled = MutableStateFlow(false)
    override val videoEnabled: Flow<Boolean> = _videoEnabled.asStateFlow()

    private var engine: WebRtcCallEngine? = rtcEngine
    private var iceForwardJob: Job? = null
    private var ringTimeoutJob: Job? = null
    private var connectedAt: Long = 0L
    private var currentPeer: String? = null
    private var wasOutgoing: Boolean = false

    // --- CallSession ---------------------------------------------------

    override suspend fun startCall(peerAccountId: String, media: CallMedia): Result<String> {
        if (_state.value !is CallSessionState.Idle) {
            return Result.failure(ChattlyError.Validation("call", "error_call_already_active"))
        }
        val callId = UuidV7.generate().toString()
        val engine = this.engine ?: rtcEngine.also { this.engine = it }
        watchEngine(engine, callId)

        val offer = try {
            engine.startOutgoingCall(
                CallInfo(callId, peerAccountId, media.toRtc(), isOutgoing = true),
            )
        } catch (e: Exception) {
            Timber.w(e, "WebRTC offer failed")
            return Result.failure(ChattlyError.Unknown(e))
        }

        currentPeer = peerAccountId
        wasOutgoing = true
        val sent = sendSignal(
            peer = peerAccountId,
            signal = CallSignal(
                kind = CallSignalKind.RING,
                callId = callId,
                sdpType = "offer",
                sdp = offer.sdp,
                iceCandidate = null,
                media = media,
                sentAtMs = System.currentTimeMillis(),
            ),
        )
        if (!sent) {
            runCatching { engine.endCall(callId) }
            this.engine = null
            return Result.failure(ChattlyError.Network())
        }

        _state.value = CallSessionState.Outgoing(callId, peerAccountId, media)
        ringTimeoutJob = scope.launch {
            kotlinx.coroutines.delay(RING_TIMEOUT_MS)
            if (_state.value is CallSessionState.Outgoing) {
                Timber.d("Ring timeout; cancelling call")
                sendSignal(
                    peer = peerAccountId,
                    signal = controlSignal(CallSignalKind.CANCEL, callId, media),
                )
                finishCall(callId, connected = false, direction = CallDirection.OUTGOING, media = media)
            }
        }
        return Result.success(callId)
    }

    override suspend fun accept(): Result<Unit> {
        val incoming = _state.value as? CallSessionState.Incoming
            ?: return Result.failure(ChattlyError.Validation("call", "error_call_not_ringing"))
        val pending = pendingOffer ?: return Result.failure(ChattlyError.Validation("call", "error_call_not_ringing"))

        val activeEngine = engine ?: rtcEngine.also { engine = it }
        watchEngine(activeEngine, incoming.callId)
        val answer = try {
            activeEngine.acceptIncomingCall(
                CallInfo(incoming.callId, incoming.peerAccountId, incoming.media.toRtc(), isOutgoing = false),
                LocalSdp(SdpType.OFFER, pending),
            )
        } catch (e: Exception) {
            Timber.w(e, "WebRTC answer failed")
            return Result.failure(ChattlyError.Unknown(e))
        }
        sendSignal(
            peer = incoming.peerAccountId,
            signal = CallSignal(
                kind = CallSignalKind.ACCEPT,
                callId = incoming.callId,
                sdpType = "answer",
                sdp = answer.sdp,
                iceCandidate = null,
                media = incoming.media,
                sentAtMs = System.currentTimeMillis(),
            ),
        )
        pendingOffer = null
        return Result.success(Unit)
    }

    override suspend fun decline() {
        val incoming = _state.value as? CallSessionState.Incoming ?: return
        sendSignal(
            peer = incoming.peerAccountId,
            signal = controlSignal(CallSignalKind.DECLINE, incoming.callId, incoming.media),
        )
        finishCall(incoming.callId, connected = false, direction = CallDirection.INCOMING, media = incoming.media)
    }

    override suspend fun hangUp() {
        val snapshot = _state.value
        when (snapshot) {
            is CallSessionState.Active -> {
                sendSignal(
                    peer = snapshot.peerAccountId,
                    signal = controlSignal(CallSignalKind.HANGUP, snapshot.callId, snapshot.media),
                )
                finishCall(
                    snapshot.callId,
                    connected = true,
                    direction = if (wasOutgoing) CallDirection.OUTGOING else CallDirection.INCOMING,
                    media = snapshot.media,
                )
            }
            is CallSessionState.Outgoing -> {
                sendSignal(
                    peer = snapshot.peerAccountId,
                    signal = controlSignal(CallSignalKind.CANCEL, snapshot.callId, snapshot.media),
                )
                finishCall(snapshot.callId, connected = false, direction = CallDirection.OUTGOING, media = snapshot.media)
            }
            is CallSessionState.Incoming -> decline()
            CallSessionState.Idle -> Unit
        }
    }

    override suspend fun setMuted(muted: Boolean) {
        _muted.value = muted
        engine?.setMuted(muted)
    }

    override suspend fun setVideoEnabled(enabled: Boolean) {
        _videoEnabled.value = enabled
        engine?.setVideoEnabled(enabled)
    }

    override suspend fun setSpeakerphone(enabled: Boolean) {
        engine?.setSpeakerphone(enabled)
    }

    override suspend fun restartIce() {
        engine?.restartIce()
    }

    // --- CallHistoryRepository ----------------------------------------

    override fun observeLog(): Flow<List<CallLogEntry>> =
        callLogDao.observeRecent().map { rows ->
            rows.map { row ->
                CallLogEntry(
                    id = row.id,
                    peerAccountId = row.peerAccountId,
                    direction = when (row.direction) {
                        "incoming" -> CallDirection.INCOMING
                        "missed" -> CallDirection.MISSED
                        else -> CallDirection.OUTGOING
                    },
                    media = if (row.media == "video") CallMedia.VIDEO else CallMedia.AUDIO,
                    startedAt = row.startedAt,
                    durationMs = row.durationMs,
                )
            }
        }

    override suspend fun record(entry: CallLogEntry) = withContext(ioDispatcher) {
        callLogDao.insert(
            CallLogEntity(
                id = entry.id,
                peerAccountId = entry.peerAccountId,
                direction = when (entry.direction) {
                    CallDirection.INCOMING -> "incoming"
                    CallDirection.MISSED -> "missed"
                    CallDirection.OUTGOING -> "outgoing"
                },
                media = if (entry.media == CallMedia.VIDEO) "video" else "audio",
                startedAt = entry.startedAt,
                durationMs = entry.durationMs,
            ),
        )
    }

    // --- Inbound signalling (called by the realtime coordinator) ------

    private var pendingOffer: String? = null

    /** Decrypts + parses one relayed call-signal payload, then routes it. */
    suspend fun onEncryptedSignal(senderAccountId: String, ciphertext: ByteArray) {
        val peerKey = peerKeyResolver.publicKeyFor(senderAccountId) ?: return
        val plaintext = try {
            cipher.decrypt(peerKey, ciphertext)
        } catch (e: Exception) {
            Timber.w(e, "Call signal decrypt failed")
            return
        }
        val content = try {
            CallSignalContent.parseFrom(plaintext)
        } catch (e: Exception) {
            Timber.w(e, "Call signal unparseable")
            return
        }
        onSignal(
            senderAccountId,
            CallSignal(
                kind = content.kind.toDomain(),
                callId = content.callId,
                sdpType = content.sdpType.takeIf { it.isNotEmpty() },
                sdp = content.sdp.takeIf { it.isNotEmpty() },
                iceCandidate = content.iceCandidate.takeIf { it.isNotEmpty() },
                media = if (content.mediaType == 1) CallMedia.VIDEO else CallMedia.AUDIO,
                sentAtMs = content.sentAtMs,
                sdpMid = content.sdpMid.takeIf { it.isNotEmpty() },
                sdpMLineIndex = content.sdpMlineIndex,
            ),
        )
    }

    private fun CallSignalContent.Kind.toDomain(): CallSignalKind = when (this) {
        CallSignalContent.Kind.KIND_RING -> CallSignalKind.RING
        CallSignalContent.Kind.KIND_ACCEPT -> CallSignalKind.ACCEPT
        CallSignalContent.Kind.KIND_DECLINE -> CallSignalKind.DECLINE
        CallSignalContent.Kind.KIND_BUSY -> CallSignalKind.BUSY
        CallSignalContent.Kind.KIND_CANCEL -> CallSignalKind.CANCEL
        CallSignalContent.Kind.KIND_HANGUP -> CallSignalKind.HANGUP
        CallSignalContent.Kind.KIND_ICE_CANDIDATE -> CallSignalKind.ICE_CANDIDATE
        else -> CallSignalKind.HANGUP
    }

    suspend fun onSignal(senderAccountId: String, signal: CallSignal) {
        when (signal.kind) {
            CallSignalKind.RING -> handleRing(senderAccountId, signal)
            CallSignalKind.ACCEPT -> handleAccept(signal)
            CallSignalKind.DECLINE, CallSignalKind.BUSY, CallSignalKind.CANCEL ->
                if (matchesCurrentCall(signal)) {
                    finishCall(
                        signal.callId,
                        connected = false,
                        direction = CallDirection.OUTGOING,
                        media = signal.media,
                    )
                }
            CallSignalKind.HANGUP ->
                if (matchesCurrentCall(signal)) {
                    val active = _state.value
                    finishCall(
                        signal.callId,
                        connected = active is CallSessionState.Active,
                        direction = if (wasOutgoing) CallDirection.OUTGOING else CallDirection.INCOMING,
                        media = signal.media,
                    )
                }
            CallSignalKind.ICE_CANDIDATE -> {
                val candidate = signal.iceCandidate ?: return
                engine?.addRemoteCandidate(
                    IceCandidate(candidate, signal.sdpMid, signal.sdpMLineIndex),
                )
            }
        }
    }

    private suspend fun handleRing(sender: String, signal: CallSignal) {
        if (_state.value !is CallSessionState.Idle) {
            sendSignal(
                peer = sender,
                signal = controlSignal(CallSignalKind.BUSY, signal.callId, signal.media),
            )
            return
        }
        pendingOffer = signal.sdp
        currentPeer = sender
        wasOutgoing = false
        _state.value = CallSessionState.Incoming(signal.callId, sender, signal.media)
        callNotifications.showIncomingCall(sender)
        ringTimeoutJob = scope.launch {
            kotlinx.coroutines.delay(RING_TIMEOUT_MS)
            if (_state.value is CallSessionState.Incoming) {
                finishCall(
                    signal.callId,
                    connected = false,
                    direction = CallDirection.MISSED,
                    media = signal.media,
                )
            }
        }
    }

    private suspend fun handleAccept(signal: CallSignal) {
        val outgoing = _state.value as? CallSessionState.Outgoing ?: return
        if (signal.callId != outgoing.callId) return
        val sdp = signal.sdp ?: return
        try {
            engine?.applyRemoteSdp(LocalSdp(SdpType.ANSWER, sdp))
        } catch (e: Exception) {
            Timber.w(e, "Remote answer rejected")
            finishCall(outgoing.callId, connected = false, direction = CallDirection.OUTGOING, media = outgoing.media)
        }
    }

    private fun matchesCurrentCall(signal: CallSignal): Boolean {
        val snapshot = _state.value
        return when (snapshot) {
            is CallSessionState.Active -> snapshot.callId == signal.callId
            is CallSessionState.Outgoing -> snapshot.callId == signal.callId
            is CallSessionState.Incoming -> snapshot.callId == signal.callId
            CallSessionState.Idle -> false
        }
    }

    // --- Internals ------------------------------------------------------

    private fun watchEngine(activeEngine: WebRtcCallEngine, callId: String) {
        iceForwardJob?.cancel()
        iceForwardJob = scope.launch {
            activeEngine.iceCandidates.collect { candidate ->
                val peer = currentPeer ?: return@collect
                sendSignal(
                    peer = peer,
                    signal = CallSignal(
                        kind = CallSignalKind.ICE_CANDIDATE,
                        callId = callId,
                        sdpType = null,
                        sdp = null,
                        iceCandidate = candidate.candidate,
                        media = callMediaOf(_state.value),
                        sentAtMs = System.currentTimeMillis(),
                        sdpMid = candidate.sdpMid,
                        sdpMLineIndex = candidate.sdpMLineIndex,
                    ),
                )
            }
        }
        scope.launch {
            activeEngine.state.collect { engineState ->
                if (engineState == CallState.CONNECTED) {
                    connectedAt = System.currentTimeMillis()
                    val snapshot = _state.value
                    if (snapshot is CallSessionState.Outgoing) {
                        _state.value = CallSessionState.Active(
                            snapshot.callId,
                            snapshot.peerAccountId,
                            snapshot.media,
                            connectedAt,
                        )
                    } else if (snapshot is CallSessionState.Incoming) {
                        _state.value = CallSessionState.Active(
                            snapshot.callId,
                            snapshot.peerAccountId,
                            snapshot.media,
                            connectedAt,
                        )
                    }
                } else if (engineState == CallState.FAILED) {
                    val snapshot = _state.value
                    if (snapshot is CallSessionState.Active) {
                        finishCall(
                            snapshot.callId,
                            connected = true,
                            direction = CallDirection.OUTGOING,
                            media = snapshot.media,
                        )
                    }
                }
            }
        }
    }

    private fun callMediaOf(snapshot: CallSessionState): CallMedia = when (snapshot) {
        is CallSessionState.Active -> snapshot.media
        is CallSessionState.Incoming -> snapshot.media
        is CallSessionState.Outgoing -> snapshot.media
        CallSessionState.Idle -> CallMedia.AUDIO
    }

    private fun controlSignal(kind: CallSignalKind, callId: String, media: CallMedia) = CallSignal(
        kind = kind,
        callId = callId,
        sdpType = null,
        sdp = null,
        iceCandidate = null,
        media = media,
        sentAtMs = System.currentTimeMillis(),
    )

    private suspend fun finishCall(
        callId: String,
        connected: Boolean,
        direction: CallDirection,
        media: CallMedia,
    ) {
        callNotifications.dismiss()
        ringTimeoutJob?.cancel()
        ringTimeoutJob = null
        iceForwardJob?.cancel()
        iceForwardJob = null

        val duration = if (connected && connectedAt > 0L) {
            System.currentTimeMillis() - connectedAt
        } else {
            0L
        }
        val peer = currentPeer
        if (peer != null) {
            record(
                CallLogEntry(
                    id = UuidV7.generate().toString(),
                    peerAccountId = peer,
                    direction = when {
                        direction == CallDirection.MISSED -> CallDirection.MISSED
                        connected -> direction
                        !wasOutgoing -> CallDirection.MISSED
                        else -> direction
                    },
                    media = media,
                    startedAt = if (connectedAt > 0L) connectedAt else System.currentTimeMillis(),
                    durationMs = duration,
                ),
            )
        }

        runCatching { engine?.endCall(callId) }
        engine = null
        pendingOffer = null
        currentPeer = null
        connectedAt = 0L
        _muted.value = false
        _videoEnabled.value = false
        _state.value = CallSessionState.Idle
    }

    private suspend fun sendSignal(peer: String, signal: CallSignal): Boolean {
        val peerKey = peerKeyResolver.publicKeyFor(peer) ?: return false
        val content = CallSignalContent.newBuilder()
            .setKind(signal.kind.toProto())
            .setCallId(signal.callId)
            .apply { signal.sdpType?.let(::setSdpType) }
            .apply { signal.sdp?.let(::setSdp) }
            .apply { signal.iceCandidate?.let(::setIceCandidate) }
            .apply { signal.sdpMid?.let(::setSdpMid) }
            .setSdpMlineIndex(signal.sdpMLineIndex)
            .setMediaType(if (signal.media == CallMedia.VIDEO) 1 else 0)
            .setSentAtMs(signal.sentAtMs)
            .build()
        val ciphertext = try {
            cipher.encrypt(peerKey, content.toByteArray())
        } catch (e: Exception) {
            Timber.w(e, "Call signal encrypt failed")
            return false
        }
        return realtime.send(
            Frame.newBuilder()
                .setCallSignal(
                    CallSignalFrame.newBuilder()
                        .setPeerAccountId(peer)
                        .setCallId(signal.callId)
                        .setCiphertext(ByteString.copyFrom(ciphertext)),
                )
                .build(),
        )
    }

    private fun CallSignalKind.toProto(): CallSignalContent.Kind = when (this) {
        CallSignalKind.RING -> CallSignalContent.Kind.KIND_RING
        CallSignalKind.ACCEPT -> CallSignalContent.Kind.KIND_ACCEPT
        CallSignalKind.DECLINE -> CallSignalContent.Kind.KIND_DECLINE
        CallSignalKind.BUSY -> CallSignalContent.Kind.KIND_BUSY
        CallSignalKind.CANCEL -> CallSignalContent.Kind.KIND_CANCEL
        CallSignalKind.HANGUP -> CallSignalContent.Kind.KIND_HANGUP
        CallSignalKind.ICE_CANDIDATE -> CallSignalContent.Kind.KIND_ICE_CANDIDATE
    }

    private fun CallMedia.toRtc(): CallMediaType = when (this) {
        CallMedia.AUDIO -> CallMediaType.AUDIO
        CallMedia.VIDEO -> CallMediaType.VIDEO
    }

    /** Tears down without recording (for tests/sign-out). */
    fun cancel() {
        scope.cancel()
    }

    companion object {
        const val RING_TIMEOUT_MS = 45_000L
    }
}


