package com.chattlyx.app.telecom

import android.net.Uri
import android.os.Bundle
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import com.chattlyx.domain.calls.CallSession
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * CALL-06 (Phase 8): self-managed ConnectionService. Android instantiates
 * this class directly, so dependencies are pulled through a Hilt entry point
 * rather than injection. Signatures follow the public API 23 contract
 * (`onCreate{Incoming,Outgoing}Connection(PhoneAccountHandle, ConnectionRequest)`).
 */
class ChattlyxCallService : ConnectionService() {

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ): Connection = buildConnection(request)

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ): Connection = buildConnection(request)

    private fun buildConnection(request: ConnectionRequest?): Connection {
        val extras = resolveExtras(request?.extras)
        val callId = extras?.getString(EXTRA_CALL_ID).orEmpty()
        val peer = extras?.getString(EXTRA_PEER_ACCOUNT_ID).orEmpty()

        val connection = SelfManagedConnection(applicationContext, callId, peer)
        connection.setConnectionProperties(Connection.PROPERTY_SELF_MANAGED)
        connection.setCallerDisplayName(peer.take(DISPLAY_NAME_CHARS), TelecomManager.PRESENTATION_ALLOWED)
        connection.setAddress(
            Uri.fromParts("tel", ADDRESS_PLACEHOLDER, null),
            TelecomManager.PRESENTATION_RESTRICTED,
        )
        ownConnection = connection
        activeConnection = connection
        return connection
    }

    /** Incoming-call extras arrive nested under EXTRA_INCOMING_CALL_EXTRAS. */
    private fun resolveExtras(bundle: Bundle?): Bundle? {
        if (bundle == null) return null
        val nested = runCatching {
            bundle.getParcelable<Bundle>(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS)
        }.getOrNull()
        return nested ?: bundle
    }

    private var ownConnection: SelfManagedConnection? = null

    override fun onDestroy() {
        if (activeConnection === ownConnection) {
            activeConnection = null
        }
        super.onDestroy()
    }

    /**
     * One live call at a time (CallManager enforces the same invariant), so
     * a single registry slot is enough for the notifier to tear the call down.
     */
    companion object {
        const val EXTRA_CALL_ID = "chattlyx_call_id"
        const val EXTRA_PEER_ACCOUNT_ID = "chattlyx_peer_account_id"

        private const val DISPLAY_NAME_CHARS = 8
        private const val ADDRESS_PLACEHOLDER = "0"

        @Volatile
        private var activeConnection: SelfManagedConnection? = null

        /** Called by [TelecomCallNotifier] when the app's call ends. */
        fun markDisconnected() {
            val connection = activeConnection ?: return
            activeConnection = null
            connection.setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
            connection.release()
            connection.destroy()
        }
    }
}

/**
 * One system-side call. Answer/reject/hang-up from system surfaces (dialer,
 * lock screen, Bluetooth, car) delegate into the app's E2EE call session.
 */
class SelfManagedConnection(
    context: android.content.Context,
    val callId: String,
    val peerAccountId: String,
) : Connection() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val session: CallSession = EntryPointAccessors.fromApplication(
        context.applicationContext,
        CallSessionEntryPoint::class.java,
    ).callSession()

    override fun onAnswer() {
        scope.launch { session.accept() }
    }

    override fun onReject() {
        scope.launch { session.decline() }
    }

    override fun onDisconnect() {
        scope.launch { session.hangUp() }
    }

    /** Stops the delegation scope once the platform tears the call down. */
    fun release() {
        scope.cancel()
    }
}

/** Hilt bridge: system-created services cannot be field-injected. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface CallSessionEntryPoint {
    fun callSession(): CallSession
}
