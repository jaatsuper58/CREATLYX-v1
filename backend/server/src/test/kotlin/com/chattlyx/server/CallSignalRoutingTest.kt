package com.chattlyx.server

import com.chattlyx.proto.CallSignalFrame
import com.chattlyx.server.messaging.ConnectionRegistry
import com.chattlyx.server.websocket.CallSignalOutcome
import com.chattlyx.server.websocket.routeCallSignal
import com.google.protobuf.ByteString
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** CALL-* relay rules without sockets: offline/invalid/self/empty cases. */
class CallSignalRoutingTest {

    private val registry = ConnectionRegistry()
    private val sender = UUID.randomUUID()
    private val peer = UUID.randomUUID()

    private fun signal(peerId: String, payload: String = "ct", callId: String = "c1") =
        CallSignalFrame.newBuilder()
            .setPeerAccountId(peerId)
            .setCallId(callId)
            .setCiphertext(ByteString.copyFromUtf8(payload))
            .build()

    @Test
    fun `offline peer is reported`() {
        val outcome = routeCallSignal(registry, sender, signal(peer.toString()))
        assertIs<CallSignalOutcome.PeerOffline>(outcome)
    }

    @Test
    fun `malformed peer id is invalid`() {
        val outcome = routeCallSignal(registry, sender, signal("not-a-uuid"))
        assertIs<CallSignalOutcome.Invalid>(outcome)
    }

    @Test
    fun `signalling self is invalid`() {
        val outcome = routeCallSignal(registry, sender, signal(sender.toString()))
        assertIs<CallSignalOutcome.Invalid>(outcome)
    }

    @Test
    fun `empty ciphertext is invalid`() {
        val frame = CallSignalFrame.newBuilder()
            .setPeerAccountId(peer.toString())
            .setCallId("c1")
            .build()
        val outcome = routeCallSignal(registry, sender, frame)
        assertIs<CallSignalOutcome.Invalid>(outcome)
    }

    @Test
    fun `blocked pairs are suppressed silently`() {
        val gate = object : com.chattlyx.backend.messaging.BlockGate {
            override fun blocksEitherWay(a: UUID, b: UUID) = a == sender && b == peer
        }
        assertIs<CallSignalOutcome.Suppressed>(
            routeCallSignal(registry, sender, signal(peer.toString()), gate),
        )
    }

    @Test
    fun `registry liveness gates forwarding`() {
        // No sessions registered: nobody is live.
        assertTrue(!registry.isLive(peer))
        assertIs<CallSignalOutcome.PeerOffline>(
            routeCallSignal(registry, sender, signal(peer.toString())),
        )
    }
}
