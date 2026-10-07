package com.chattlyx.backend.messaging

import java.util.UUID

/**
 * SAF-02 (Phase 7): delivery-suppression gate between blocked peers.
 * Implementations must be cheap — `send()` consults the gate per message.
 */
interface BlockGate {

    /** True when either account blocks the other (direction-agnostic). */
    fun blocksEitherWay(a: UUID, b: UUID): Boolean

    companion object {
        /** Default for tests/modules without a block store: nothing is blocked. */
        val OPEN: BlockGate = object : BlockGate {
            override fun blocksEitherWay(a: UUID, b: UUID): Boolean = false
        }
    }
}
