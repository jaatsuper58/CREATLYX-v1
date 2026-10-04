package com.chattlyx.core.network

import kotlin.math.min
import kotlin.random.Random

/**
 * Reconnect back-off policy for the realtime socket (Section 6.5):
 * exponential 1s -> 60s with +/-20 % jitter to avoid thundering herds after
 * outages.
 */
class ReconnectBackoff(
    private val minDelayMillis: Long = MIN_DELAY_MS,
    private val maxDelayMillis: Long = MAX_DELAY_MS,
    private val multiplier: Double = MULTIPLIER,
    private val jitterFraction: Double = JITTER_FRACTION,
    private val random: Random = Random.Default,
) {

    /** Zero-based attempt number -> delay before the next connection attempt. */
    fun delayMillis(attempt: Int): Long {
        require(attempt >= 0) { "attempt must be >= 0" }
        val exponential = minDelayMillis * Math.pow(multiplier, attempt.toDouble())
        val capped = min(exponential.toLong(), maxDelayMillis)
        val jitter = (capped * jitterFraction * (2.0 * random.nextDouble() - 1.0)).toLong()
        return (capped + jitter).coerceIn(0L, maxDelayMillis)
    }

    companion object {
        const val MIN_DELAY_MS = 1_000L
        const val MAX_DELAY_MS = 60_000L
        const val MULTIPLIER = 2.0
        const val JITTER_FRACTION = 0.2
    }
}

/**
 * Adaptive heartbeat interval (Section 6.5): 30 s in the foreground on
 * unmetered networks up to 240 s in the background on battery saver.
 */
class AdaptiveHeartbeat {

    fun intervalMillis(
        networkType: NetworkType,
        foreground: Boolean,
        batterySaver: Boolean,
    ): Long {
        var interval = BASE_FOREGROUND_MS

        if (!foreground) interval *= 2
        if (networkType == NetworkType.CELLULAR) interval += CELLULAR_PENALTY_MS
        if (batterySaver) interval *= 2

        return interval.coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)
    }

    companion object {
        const val BASE_FOREGROUND_MS = 30_000L
        const val CELLULAR_PENALTY_MS = 60_000L
        const val MIN_INTERVAL_MS = 30_000L
        const val MAX_INTERVAL_MS = 240_000L
    }
}
