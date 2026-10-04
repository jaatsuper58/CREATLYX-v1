package com.chattlyx.core.push

/**
 * Zero-plaintext push contract (NOT-01): the FCM data message carries only an
 * opaque envelope id; the handler fetches and decrypts locally. Phase 2 adds
 * the FCM service implementation; Phase 0 ships the contract + no-op.
 */
interface PushWakeHandler {
    /** Called with the opaque envelope id from a data-only FCM message. */
    suspend fun onEnvelopeAvailable(envelopeId: String)

    /** FCM token rotation hook (AUTH-06 registration flow). */
    suspend fun onTokenRefreshed(token: String)
}

object NoOpPushWakeHandler : PushWakeHandler {
    override suspend fun onEnvelopeAvailable(envelopeId: String) {
        // Phase 0: push pipeline not wired yet; contract is frozen.
    }

    override suspend fun onTokenRefreshed(token: String) {
        // Phase 0: token registration lands with device registration.
    }
}
