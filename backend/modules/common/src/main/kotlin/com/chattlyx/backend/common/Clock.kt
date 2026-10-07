package com.chattlyx.backend.common

/** Injectable UTC clock; tests substitute fixed clocks (clock-skew scenarios). */
interface Clock {
    fun nowMillis(): Long
}

object SystemUtcClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
