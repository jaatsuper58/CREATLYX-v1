package com.chattlyx.core.network

import com.chattlyx.proto.Frame
import kotlinx.coroutines.flow.Flow

/** Realtime socket states (Section 6.5). */
enum class RealtimeConnectionState {
    DISCONNECTED,
    CONNECTING,
    AUTHENTICATING,
    CONNECTED,
}

/**
 * Contract for the single multiplexed WebSocket (`/v1/ws`, master spec 8.3).
 * Binary Protobuf frames; the client authenticates with an AuthFrame, keeps
 * the link alive with PingFrames and drains DeliverFrames from [incomingFrames].
 */
interface RealtimeClient {

    val connectionState: Flow<RealtimeConnectionState>

    /** Server -> client frames (deliver, ack, receipt, typing, error, pong). */
    val incomingFrames: Flow<Frame>

    /** Connect with the current access token; idempotent while connected. */
    suspend fun connect(accessToken: String, deviceId: Long)

    /** Graceful disconnect (logout, user switch). Stops auto-reconnect. */
    suspend fun disconnect()

    /** Force an immediate reconnect attempt (network change, FCM wake). */
    fun requestReconnect()

    /** Sends a frame; false when no socket is open (caller should sync later). */
    suspend fun send(frame: Frame): Boolean
}
