package com.chattlyx.core.push

import com.google.firebase.messaging.FirebaseMessaging
import timber.log.Timber

/**
 * Fetches the current FCM registration token (NOT-01). Fails soft on
 * devices without Play services or before google-services configuration.
 */
object PushTokenRegistrar {

    fun registerCurrent(onToken: (String) -> Unit) {
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    task.result?.let(onToken)
                } else {
                    Timber.i("FCM token unavailable (Play services or config missing)")
                }
            }
        } catch (e: Exception) {
            Timber.i(e, "FirebaseMessaging not initialised; push disabled")
        }
    }
}
