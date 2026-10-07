package com.chattlyx.backend.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RateLimiterTest {

    private var now = 1_000_000L
    private val limiter = RateLimiter { now }

    @Test
    fun `allows up to the limit then blocks`() {
        assertNull(limiter.tryAcquire("k", maxEvents = 3, windowMillis = 10_000))
        assertNull(limiter.tryAcquire("k", maxEvents = 3, windowMillis = 10_000))
        assertNull(limiter.tryAcquire("k", maxEvents = 3, windowMillis = 10_000))
        assertNotNull(limiter.tryAcquire("k", maxEvents = 3, windowMillis = 10_000))
    }

    @Test
    fun `window expiry frees the slot`() {
        repeat(3) { limiter.tryAcquire("k", 3, 10_000) }
        assertNotNull(limiter.tryAcquire("k", 3, 10_000))
        now += 10_001
        assertNull(limiter.tryAcquire("k", 3, 10_000))
    }

    @Test
    fun `keys are independent`() {
        repeat(3) { limiter.tryAcquire("a", 3, 10_000) }
        assertNull(limiter.tryAcquire("b", 3, 10_000))
    }

    @Test
    fun `retry-after reports remaining window`() {
        repeat(3) { limiter.tryAcquire("k", 3, 10_000) }
        now += 4_000
        val waitMs = limiter.tryAcquire("k", 3, 10_000)
        assertEquals(6_000L, waitMs)
    }
}
