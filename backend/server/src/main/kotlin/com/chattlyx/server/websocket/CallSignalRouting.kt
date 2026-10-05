package com.chattlyx.server.websocket

import com.chattlyx.proto.CallSignalFrame
import com.chattlyx.proto.Frame
import com.chattlyx.server.messaging.ConnectionRegistry
import io.ktor.websocket.Frame as WsFrame
import java.util.UUID

/** Outcome of routing one CALL-* signal frame. */
sealed interface CallSignalOutcome {
    /** Relayed to the peer; carries the wire frame for tests/inspection. */
    data class Forwarded(val frame: WsFrame) : CallSignalOutcome

    /** Peer has no live socket; caller gets an ErrorFrame. */
    data object PeerOffline : CallSignalOutcome

    /** Malformed/self-targeted signal; caller gets an ErrorFrame. */
    data class Invalid(val reason: String) : CallSignalOutcome
}

/**
 * CALL-* zero-knowledge signalling relay: the ciphertext is opaque to the
 * server and only forwarded to the addressed peer. `peer_account_id` is
 * flipped on relay so the receiving client always sees the remote party's id
 * in the same field.
 */
fun routeCallSignal(
    registry: ConnectionRegistry,
    sender: UUID,
    signal: CallSignalFrame,
): CallSignalOutcome {
    val peer = try {
        UUID.fromString(signal.peerAccountId)
    } catch (e: IllegalArgumentException) {
        return CallSignalOutcome.Invalid("peer_account_id must be a UUID")
    }
    if (peer == sender) {
        return CallSignalOutcome.Invalid("cannot signal self")
    }
    if (signal.ciphertext.isEmpty) {
        return CallSignalOutcome.Invalid("empty signalling payload")
    }
    if (!registry.isLive(peer)) {
        return CallSignalOutcome.PeerOffline
    }

    val wire = WsFrame.Binary(
        true,
        Frame.newBuilder()
            .setCallSignal(
                CallSignalFrame.newBuilder()
                    .setPeerAccountId(sender.toString())
                    .setCallId(signal.callId)
                    .setCiphertext(signal.ciphertext),
            )
            .build()
            .toByteArray(),
    )
    registry.broadcast(peer, wire)
    return CallSignalOutcome.Forwarded(wire)
}
