package com.chattlyx.core.common.logging

/**
 * Scrubs log strings so message content patterns, phone numbers and bearer
 * tokens never reach log sinks (master spec Section 9.2). Applied by the
 * Timber tree and any future logging surface.
 */
object SafeLog {

    private val PHONE_PATTERN = Regex("""\+?\d[\d\s\-()]{6,}\d""")
    private val BEARER_PATTERN = Regex("""(?i)bearer\s+[A-Za-z0-9\-._~+/]+=*""")
    private val TOKEN_PATTERN = Regex("""(?i)(token|authorization|apikey|otp)[=:]\s*\S+""")
    private val DIGIT_RUN_PATTERN = Regex("""\d{7,}""")

    fun scrub(message: String): String = message
        .let { BEARER_PATTERN.replace(it, "bearer=<redacted>") }
        .let { TOKEN_PATTERN.replace(it) { match -> match.value.substringBefore(Regex("[=:]")) + "=<redacted>" } }
        .let { PHONE_PATTERN.replace(it, "<phone-redacted>") }
        .let { DIGIT_RUN_PATTERN.replace(it, "<digits-redacted>") }
}
