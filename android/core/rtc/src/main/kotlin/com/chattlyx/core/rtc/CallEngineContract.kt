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

/** One trickle-ICE candidate produced by the local engine. */
data class IceCandidate(val candidate: String, val sdpMid: String?, val sdpMLineIndex: Int)

interface CallEngine {
    val state: Flow<CallState>

    /** EGL context the UI must pass to `SurfaceViewRenderer.init`. */
    val eglContext: org.webrtc.EglBase.Context

    /** Outgoing SDP/ICE the app must carry to the peer over the E2EE channel. */
    val localSdpEvents: Flow<LocalSdp>

    /** Trickle candidates to relay to the peer. */
    val iceCandidates: Flow<IceCandidate>

    /**
     * Creates the peer connection and returns the SDP offer. The app sends it
     * inside the encrypted RING signal.
     */
    suspend fun startOutgoingCall(info: CallInfo): LocalSdp

    /** Sets the remote offer and returns the SDP answer for ACCEPT. */
    suspend fun acceptIncomingCall(info: CallInfo, offer: LocalSdp): LocalSdp

    /** Applies the remote answer (caller side) once ACCEPT arrives. */
    suspend fun applyRemoteSdp(sdp: LocalSdp)

    /** Applies a remote trickle candidate. */
    suspend fun addRemoteCandidate(candidate: IceCandidate)

    suspend fun endCall(callId: String)

    suspend fun setMuted(muted: Boolean)
    suspend fun setSpeakerphone(enabled: Boolean)
    suspend fun setVideoEnabled(enabled: Boolean)

    /** ICE restart on Wi-Fi <-> mobile switch must not drop the call (CALL-06). */
    suspend fun restartIce()

    /**
     * CALL-01 video (Phase 7): pipe the local or remote video track into a
     * [org.webrtc.SurfaceViewRenderer] the UI owns. Safe to call before the
     * remote track arrives; the sink attaches as soon as it does.
     */
    fun attachVideoRenderer(renderer: org.webrtc.SurfaceViewRenderer, remote: Boolean)

    /** Stops piping frames into [renderer] (both directions). */
    fun detachVideoRenderer(renderer: org.webrtc.SurfaceViewRenderer)
}

/** A session description plus its type (offer/answer). */
data class LocalSdp(val type: SdpType, val sdp: String)

enum class SdpType { OFFER, ANSWER }
