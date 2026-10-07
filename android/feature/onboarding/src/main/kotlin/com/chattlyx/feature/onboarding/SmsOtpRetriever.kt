package com.chattlyx.feature.onboarding

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import timber.log.Timber

/**
 * SMS Retriever auto-read for the OTP (AUTH-03 convenience). Active only on
 * GMS devices; every failure path degrades silently to manual entry, so
 * non-GMS devices are unaffected. No SMS permission is required or used.
 */
class SmsOtpRetriever(private val context: Context) {

    private var receiver: BroadcastReceiver? = null

    /** Starts listening; returns false when GMS is unavailable. */
    fun start(onCode: (String) -> Unit): Boolean = try {
        SmsRetriever.getClient(context).startSmsRetriever().addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Timber.d("SMS Retriever unavailable; manual OTP entry only")
                return@addOnCompleteListener
            }
            registerReceiver(onCode)
        }
        true
    } catch (e: Exception) {
        Timber.d(e, "SMS Retriever not present on this device")
        false
    }

    fun stop() {
        receiver?.let {
            runCatching { context.unregisterReceiver(it) }
        }
        receiver = null
    }

    private fun registerReceiver(onCode: (String) -> Unit) {
        val smsReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != SmsRetriever.SMS_RETRIEVED_ACTION) return
                val extras = intent.extras ?: return
                val status = extras.get(SmsRetriever.EXTRA_STATUS) as? Status ?: return

                when (status.statusCode) {
                    CommonStatusCodes.SUCCESS -> {
                        val message = extras.getString(SmsRetriever.EXTRA_SMS_MESSAGE).orEmpty()
                        extractCode(message)?.let(onCode)
                    }

                    else -> Timber.d("SMS Retriever finished without a code")
                }
                stop()
            }
        }

        val filter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(smsReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(smsReceiver, filter)
        }
        receiver = smsReceiver
    }

    private fun extractCode(message: String): String? =
        Regex("\\b\\d{6}\\b").find(message)?.value
}
