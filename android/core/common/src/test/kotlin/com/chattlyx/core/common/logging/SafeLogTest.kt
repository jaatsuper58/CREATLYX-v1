package com.chattlyx.core.common.logging

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SafeLogTest {

    @Test
    fun `phone numbers are redacted`() {
        val scrubbed = SafeLog.scrub("OTP sent to +91 98765 43210 ok")
        assertFalse(scrubbed.contains("98765"))
        assertTrue(scrubbed.contains("<phone-redacted>"))
    }

    @Test
    fun `bearer tokens are redacted`() {
        val scrubbed = SafeLog.scrub("Authorization: Bearer abc.def-ghi_jkl=")
        assertFalse(scrubbed.contains("abc.def"))
        assertTrue(scrubbed.contains("bearer=<redacted>"))
    }

    @Test
    fun `key-value secrets are redacted`() {
        val scrubbed = SafeLog.scrub("refresh token=eyJhbGciOiJub25lIn0 otp=123456")
        assertFalse(scrubbed.contains("eyJhbGci"))
        assertFalse(scrubbed.contains("otp=123456"))
    }

    @Test
    fun `plain diagnostics survive untouched`() {
        val message = "WebSocket reconnect attempt 3 after backoff"
        // "reconnect" contains no 7+ digit run; expect content preserved.
        assertTrue(SafeLog.scrub(message).startsWith("WebSocket reconnect"))
    }
}
