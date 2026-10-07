package com.chattlyx.core.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * NOT-01: data-only FCM receiver. The payload carries ONLY an opaque
 * envelope id — no sender, no text, no phone metadata. The handler wakes
 * the realtime socket, which fetches and decrypts locally.
 */
class ChattlyxFirebaseMessagingService : FirebaseMessagingService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PushEntryPoint {
        fun pushWakeHandler(): PushWakeHandler
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val handler: PushWakeHandler by lazy {
        EntryPointAccessors.fromApplication(applicationContext, PushEntryPoint::class.java)
            .pushWakeHandler()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val envelopeId = message.data[PAYLOAD_ENVELOPE_ID]
        if (envelopeId.isNullOrBlank()) {
            Timber.d("Push without envelope id ignored")
            return
        }
        scope.launch { handler.onEnvelopeAvailable(envelopeId) }
    }

    override fun onNewToken(token: String) {
        scope.launch { handler.onTokenRefreshed(token) }
    }

    companion object {
        const val PAYLOAD_ENVELOPE_ID = "cx_env"
    }
}
