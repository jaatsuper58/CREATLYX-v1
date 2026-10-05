package com.chattlyx.domain.calls

import com.chattlyx.core.common.result.Result
import kotlinx.coroutines.flow.Flow

/** Phase 5 (CALL-*): 1:1 audio/video calls over E2EE signalling. */

enum class CallDirection { INCOMING, OUTGOING, MISSED }

enum class CallMedia { AUDIO, VIDEO }

/** One call-log row (CALL-05). */
data class CallLogEntry(
    val id: String,
    val peerAccountId: String,
    val direction: CallDirection,
    val media: CallMedia,
    val startedAt: Long,
    val durationMs: Long,
)

/** E2EE signalling payloads between two devices (CALL-01/02/04). */
enum class CallSignalKind {
    RING,
    ACCEPT,
    DECLINE,
    BUSY,
    CANCEL,
    HANGUP,
    ICE_CANDIDATE,
}

/** One decrypted call signal. */
data class CallSignal(
    val kind: CallSignalKind,
    val callId: String,
    val sdpType: String?,
    val sdp: String?,
    val iceCandidate: String?,
    val media: CallMedia,
    val sentAtMs: Long,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int = 0,
)

/** Live-call surface observed by the UI. */
sealed interface CallSessionState {
    data object Idle : CallSessionState

    data class Incoming(
        val callId: String,
        val peerAccountId: String,
        val media: CallMedia,
    ) : CallSessionState

    data class Outgoing(
        val callId: String,
        val peerAccountId: String,
        val media: CallMedia,
    ) : CallSessionState

    data class Active(
        val callId: String,
        val peerAccountId: String,
        val media: CallMedia,
        val startedAt: Long,
    ) : CallSessionState
}

/** Controls one call end-to-end (signalling + media). */
interface CallSession {

    val state: Flow<CallSessionState>

    /** True while the local microphone is muted. */
    val muted: Flow<Boolean>

    /** True while the camera is on (video calls only). */
    val videoEnabled: Flow<Boolean>

    /** CALL-01: places an outgoing call; returns the call id. */
    suspend fun startCall(peerAccountId: String, media: CallMedia): Result<String>

    /** CALL-02: answers a ringing incoming call. */
    suspend fun accept(): Result<Unit>

    /** CALL-02: rejects a ringing incoming call. */
    suspend fun decline()

    /** CALL-04: ends the active/ringing call. */
    suspend fun hangUp()

    suspend fun setMuted(muted: Boolean)
    suspend fun setVideoEnabled(enabled: Boolean)
    suspend fun setSpeakerphone(enabled: Boolean)

    /** CALL-06: renegotiate ICE without dropping the call. */
    suspend fun restartIce()
}

/** CALL-05 call history. */
interface CallHistoryRepository {
    fun observeLog(): Flow<List<CallLogEntry>>
    suspend fun record(entry: CallLogEntry)
}
