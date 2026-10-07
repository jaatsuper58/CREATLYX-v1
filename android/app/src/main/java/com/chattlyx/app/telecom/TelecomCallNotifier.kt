package com.chattlyx.app.telecom

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import com.chattlyx.app.R
import com.chattlyx.domain.calls.CallTelecomNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * CALL-06 (Phase 8): mirrors the call lifecycle into Android's telecom stack
 * via a self-managed ConnectionService (API 26+). Below API 26 this is a
 * no-op: the in-app ring surface remains the only surface.
 *
 * The integration is best-effort by design — telecom failures must never
 * break a ChattlyX call, so every platform interaction is guarded.
 */
@Singleton
class TelecomCallNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : CallTelecomNotifier {

    private val telecomManager = context.getSystemService(TelecomManager::class.java)

    val accountHandle: PhoneAccountHandle = PhoneAccountHandle(
        ComponentName(context, ChattlyxCallService::class.java),
        ACCOUNT_ID,
    )

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val account = PhoneAccount.builder(accountHandle, context.getString(R.string.app_name))
                    .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                    .setShortDescription(context.getString(R.string.app_name))
                    .build()
                telecomManager.registerPhoneAccount(account)
            }.onFailure { Timber.w(it, "PhoneAccount registration failed") }
        }
    }

    override fun onOutgoingStarted(callId: String, peerAccountId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            val extras = Bundle().apply {
                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, accountHandle)
                putString(ChattlyxCallService.EXTRA_CALL_ID, callId)
                putString(ChattlyxCallService.EXTRA_PEER_ACCOUNT_ID, peerAccountId)
            }
            telecomManager.placeCall(
                android.net.Uri.fromParts("tel", PEER_PLACEHOLDER, null),
                extras,
            )
        }.onFailure { Timber.w(it, "Telecom outgoing report failed") }
    }

    override fun onIncomingStarted(callId: String, peerAccountId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            val extras = Bundle().apply {
                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, accountHandle)
                putString(ChattlyxCallService.EXTRA_CALL_ID, callId)
                putString(ChattlyxCallService.EXTRA_PEER_ACCOUNT_ID, peerAccountId)
            }
            telecomManager.addNewIncomingCall(accountHandle, extras)
        }.onFailure { Timber.w(it, "Telecom incoming report failed") }
    }

    override fun onCallEnded() {
        // The ConnectionService owns the live Connection; it tears itself down
        // via the shared registry (see ChattlyxCallService).
        ChattlyxCallService.markDisconnected()
    }

    companion object {
        const val ACCOUNT_ID = "chattlyx-calls"

        /** Placeholder tel URI for pre-S outgoing placement (never dialled). */
        private const val PEER_PLACEHOLDER = "0000000000"
    }
}
