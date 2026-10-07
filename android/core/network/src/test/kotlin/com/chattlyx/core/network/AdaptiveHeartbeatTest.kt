package com.chattlyx.core.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AdaptiveHeartbeatTest {

    private val heartbeat = AdaptiveHeartbeat()

    @Test
    fun `foreground wifi is the base interval`() {
        assertEquals(
            30_000L,
            heartbeat.intervalMillis(NetworkType.WIFI, foreground = true, batterySaver = false),
        )
    }

    @Test
    fun `cellular adds a penalty to protect data budgets`() {
        assertEquals(
            90_000L,
            heartbeat.intervalMillis(NetworkType.CELLULAR, foreground = true, batterySaver = false),
        )
    }

    @Test
    fun `background doubles the interval`() {
        assertEquals(
            60_000L,
            heartbeat.intervalMillis(NetworkType.WIFI, foreground = false, batterySaver = false),
        )
    }

    @Test
    fun `worst case clamps at the 240 s ceiling`() {
        assertEquals(
            AdaptiveHeartbeat.MAX_INTERVAL_MS,
            heartbeat.intervalMillis(NetworkType.CELLULAR, foreground = false, batterySaver = true),
        )
    }

    @Test
    fun `never drops below the floor`() {
        assertEquals(
            AdaptiveHeartbeat.MIN_INTERVAL_MS,
            heartbeat.intervalMillis(NetworkType.WIFI, foreground = true, batterySaver = false),
        )
    }
}
