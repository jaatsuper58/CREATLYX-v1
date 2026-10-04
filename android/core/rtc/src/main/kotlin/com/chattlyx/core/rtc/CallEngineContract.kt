package com.chattlyx.core.rtc

import kotlinx.coroutines.flow.Flow

/**
 * Call-engine contract (CALL-01..06). WebRTC engine integration lands in
 * Phase 5 behind this interface; keep the module free of WebRTC dependencies
 * until then to protect startup and APK budgets (Section 6.8 lazy init).
 */
enum class CallMediaType { AUDIO, VIDEO }

enum class CallState {
    IDLE,
    RINGING,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ENDED,
    FAILED,
}

data class CallInfo(
    val callId: String,
    val peerAccountId: String,
    val mediaType: CallMediaType,
    val isOutgoing: Boolean,
)

interface CallEngine {
    val state: Flow<CallState>

    suspend fun startOutgoingCall(info: CallInfo, offer: ByteArray)
    suspend fun acceptIncomingCall(info: CallInfo, offer: ByteArray): ByteArray
    suspend fun endCall(callId: String)

    suspend fun setMuted(muted: Boolean)
    suspend fun setSpeakerphone(enabled: Boolean)
    suspend fun setVideoEnabled(enabled: Boolean)

    /** ICE restart on Wi-Fi <-> mobile switch must not drop the call (CALL-06). */
    suspend fun restartIce()
}
