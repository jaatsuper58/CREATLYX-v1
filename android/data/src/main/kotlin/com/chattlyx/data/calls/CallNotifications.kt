package com.chattlyx.data.calls

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import com.chattlyx.data.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CALL-02 (Phase 7): full-screen ring notification. Shown the moment a RING
 * signal arrives so backgrounded/screen-off devices surface the call, and
 * dismissed on any terminal call state. The intent simply opens the app —
 * the shell's ring overlay drives answer/decline.
 */
@Singleton
class CallNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        // Channels exist from API 26; below that the in-app overlay is the
        // ring surface and this class stays a no-op.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_INCOMING_CALLS,
                context.getString(R.string.call_notifications_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.call_notifications_channel_desc)
                setSound(null, null) // the in-app ringtone owns audio
                vibrationPattern = longArrayOf(0, 500, 250, 500)
            }
            manager.createNotificationChannel(channel)
        }
    }

    /** Raises the full-screen incoming-call notification for [peerDisplay]. */
    fun showIncomingCall(peerDisplay: String) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return
        val intent = PendingIntent.getActivity(
            context,
            REQUEST_INCOMING_CALL,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_INCOMING_CALLS)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle(context.getString(R.string.call_notification_title))
            .setContentText(peerDisplay)
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(true)
            .setContentIntent(intent)
            .setFullScreenIntent(intent, true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    /** Removes the ring notification (answered, declined, missed, ended). */
    fun dismiss() {
        manager.cancel(NOTIFICATION_ID)
    }

    private companion object {
        const val CHANNEL_INCOMING_CALLS = "incoming_calls"
        const val NOTIFICATION_ID = 0xC411
        const val REQUEST_INCOMING_CALL = 1
    }
}
