package com.chattlyx.core.common.error

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChattlyErrorTest {

    @Test
    fun `retryability follows the spec table`() {
        assertTrue(ChattlyError.Network().isRetryable)
        assertTrue(ChattlyError.RateLimited(retryAfterMillis = 1_000).isRetryable)
        assertTrue(ChattlyError.Server(code = "internal", httpStatus = 503).isRetryable)
        assertFalse(ChattlyError.Server(code = "blocked", httpStatus = 403).isRetryable)
        assertFalse(ChattlyError.Auth.isRetryable)
        assertFalse(ChattlyError.Crypto.KeyChanged.isRetryable)
        assertFalse(ChattlyError.Storage.Full.isRetryable)
        assertFalse(ChattlyError.Validation(field = "name", messageKey = "required").isRetryable)
        assertFalse(ChattlyError.Unknown(RuntimeException()).isRetryable)
    }

    @Test
    fun `io exceptions map to network errors`() {
        val mapped = java.io.IOException("socket closed").toChattlyError()
        assertTrue(mapped is ChattlyError.Network)
    }
}
