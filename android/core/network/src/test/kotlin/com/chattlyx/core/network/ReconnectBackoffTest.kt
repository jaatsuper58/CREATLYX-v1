package com.chattlyx.core.network

import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test

class ReconnectBackoffTest {

    private val fixedRandom = Random(42)

    @Test
    fun `delays grow exponentially from the floor`() {
        val backoff = ReconnectBackoff(jitterFraction = 0.0)
        assertEquals(1_000L, backoff.delayMillis(0))
        assertEquals(2_000L, backoff.delayMillis(1))
        assertEquals(4_000L, backoff.delayMillis(2))
        assertEquals(8_000L, backoff.delayMillis(3))
    }

    @Test
    fun `delays never exceed the cap`() {
        val backoff = ReconnectBackoff(jitterFraction = 0.0)
        assertEquals(ReconnectBackoff.MAX_DELAY_MS, backoff.delayMillis(attempt = 30))
    }

    @RepeatedTest(25)
    fun `jitter stays within the configured band`() {
        val backoff = ReconnectBackoff(random = fixedRandom)
        repeat(10) { attempt ->
            val delay = backoff.delayMillis(attempt)
            val nominal = minOf(
                (ReconnectBackoff.MIN_DELAY_MS * Math.pow(ReconnectBackoff.MULTIPLIER, attempt.toDouble())).toLong(),
                ReconnectBackoff.MAX_DELAY_MS,
            )
            val band = (nominal * ReconnectBackoff.JITTER_FRACTION).toLong()
            assertTrue(delay >= (nominal - band).coerceAtLeast(0L), "attempt=$attempt delay=$delay")
            assertTrue(delay <= nominal + band, "attempt=$attempt delay=$delay")
        }
    }
}
