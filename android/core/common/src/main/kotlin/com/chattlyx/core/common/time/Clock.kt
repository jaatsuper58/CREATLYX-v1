package com.chattlyx.core.common.time

/** Injectable wall clock; tests substitute fixed/skewed clocks (Section 11.2 #6). */
interface Clock {
    fun nowMillis(): Long
}

object SystemClock : Clock {
    override fun nowMillis(): Long = java.lang.System.currentTimeMillis()
}

/** Fixed clock for deterministic tests. */
class FixedClock(private var currentMillis: Long) : Clock {
    override fun nowMillis(): Long = currentMillis

    fun advance(millis: Long) {
        currentMillis += millis
    }
}
