package com.chattlyx.core.analytics

/**
 * Opt-in anonymous analytics (master spec Section 13). Events carry type
 * metadata only — never content, identifiers or phone numbers. The default
 * implementation is a no-op; nothing is transmitted until the user opts in.
 */
sealed class AnalyticsEvent(val name: String) {

    data object OnboardingCompleted : AnalyticsEvent("onboarding_completed")

    data class MessageSent(val type: String) : AnalyticsEvent("message_sent")

    data class CallStarted(
        val type: String,
        val outcome: String,
        val durationBucket: String,
    ) : AnalyticsEvent("call_started")

    data class UploadFailed(val reason: String) : AnalyticsEvent("upload_failed")

    data object Crash : AnalyticsEvent("crash")

    data object Anr : AnalyticsEvent("anr")
}

interface AnalyticsLogger {
    fun log(event: AnalyticsEvent)
}

object NoOpAnalyticsLogger : AnalyticsLogger {
    override fun log(event: AnalyticsEvent) {
        // Intentionally empty: analytics are opt-in and off by default.
    }
}
