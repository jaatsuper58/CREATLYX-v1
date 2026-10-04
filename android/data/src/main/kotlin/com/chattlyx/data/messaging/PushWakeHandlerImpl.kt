package com.chattlyx.data.messaging

import com.chattlyx.core.network.rest.ChattlyxServiceApi
import com.chattlyx.core.network.rest.PushTokenDto
import com.chattlyx.core.push.PushWakeHandler
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * NOT-01 wiring: push wakes the realtime socket (which then syncs); token
 * rotation is registered with the server for future wakes.
 */
@Singleton
class PushWakeHandlerImpl @Inject constructor(
    private val coordinator: RealtimeCoordinator,
    private val api: ChattlyxServiceApi,
) : PushWakeHandler {

    override suspend fun onEnvelopeAvailable(envelopeId: String) {
        // Deliberately ignore the id: sync pulls everything missed, so the
        // wake stays opaque and idempotent.
        Timber.d("Push wake received")
        coordinator.wake()
    }

    override suspend fun onTokenRefreshed(token: String) {
        val response = runCatching { api.registerPushToken(PushTokenDto(token)) }
        if (response.getOrNull()?.isSuccessful != true) {
            Timber.w("Push token registration failed; will retry on next connect")
        }
    }
}
