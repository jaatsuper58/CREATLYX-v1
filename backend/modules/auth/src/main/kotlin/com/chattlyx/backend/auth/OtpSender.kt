package com.chattlyx.backend.auth

import org.slf4j.LoggerFactory

/**
 * Pluggable OTP dispatch (master spec Section 3.2). Production wires Twilio
 * Verify / MSG91 / Firebase Phone Auth behind this interface; dev builds use
 * the fake gateway (infra/compose) which prints the code.
 */
interface OtpSender {
    fun send(e164: String, code: String)
}

/**
 * Dev-only sender: logs a redacted marker and forwards to the fake gateway
 * when configured. Never constructed in production profiles.
 */
class FakeOtpSender : OtpSender {

    override fun send(e164: String, code: String) {
        // Log the code ONLY in dev profiles; the class itself is not packaged
        // into production wiring (see AuthServices factory).
        logger.info("[dev-otp] dispatching verification code for ${redact(e164)}")
        logger.debug("[dev-otp] code={}", code)
    }

    private fun redact(e164: String): String =
        if (e164.length > 6) e164.take(3) + "*".repeat(e164.length - 6) + e164.takeLast(3) else "***"

    private companion object {
        val logger = LoggerFactory.getLogger(FakeOtpSender::class.java)
    }
}
