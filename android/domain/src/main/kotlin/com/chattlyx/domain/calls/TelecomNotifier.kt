package com.chattlyx.domain.calls

/**
 * CALL-06 (Phase 8): system telecom integration port. Implementations mirror
 * the app's call lifecycle into the platform call framework (self-managed
 * ConnectionService on API 26+) so calls show up in system call UIs and
 * Bluetooth/car controls. Data layer only reports; it never imports telecom.
 */
interface CallTelecomNotifier {

    /** An outgoing call started ringing. */
    fun onOutgoingStarted(callId: String, peerAccountId: String)

    /** An incoming call is ringing. */
    fun onIncomingStarted(callId: String, peerAccountId: String)

    /** The current call reached any terminal state. */
    fun onCallEnded()
}

/** Default for tests/contexts without telecom integration. */
object NoopCallTelecomNotifier : CallTelecomNotifier {
    override fun onOutgoingStarted(callId: String, peerAccountId: String) = Unit
    override fun onIncomingStarted(callId: String, peerAccountId: String) = Unit
    override fun onCallEnded() = Unit
}
