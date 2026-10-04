package com.chattlyx.core.network

import kotlinx.coroutines.flow.Flow

/** Realtime socket states (Section 6.5). */
enum class RealtimeConnectionState {
    DISCONNECTED,
    CONNECTING,
    AUTHENTICATING,
    CONNECTED,
}

/**
 * Contract for the single multiplexed WebSocket. The Phase 2 implementation
 * adds Protobuf frame codec, heartbeat loop, gap detection (SYNC_REQUEST) and
 * transport fallback (WSS -> HTTPS long-poll -> FCM wake).
 */
interface RealtimeClient {

    val connectionState: Flow<RealtimeConnectionState>

    /** Connect with the current access token; idempotent while connected. */
    suspend fun connect(accessToken: String)

    /** Graceful disconnect (logout, user switch). */
    suspend fun disconnect()

    /** Force an immediate reconnect attempt (network change, FCM wake). */
    fun requestReconnect()
}
