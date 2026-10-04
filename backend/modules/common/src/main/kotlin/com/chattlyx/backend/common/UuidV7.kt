package com.chattlyx.backend.common

import java.security.SecureRandom
import java.util.UUID

/**
 * UUIDv7 (RFC 9514) for server-assigned ids: time-ordered, index-friendly,
 * no coordination needed. Mirrors the client generator (monotonic within a
 * millisecond).
 */
object UuidV7 {

    private val random = SecureRandom()

    @Volatile
    private var lastTimestamp = 0L

    @Volatile
    private var lastMsb = 0L

    @Volatile
    private var lastLsb = 0L

    fun generate(nowMillis: Long = System.currentTimeMillis()): UUID = synchronized(this) {
        val timestamp = if (nowMillis > lastTimestamp) nowMillis else lastTimestamp
        val msb: Long
        val lsb: Long

        if (timestamp == lastTimestamp) {
            val next = lastLsb + 1
            if (next == 0L) {
                msb = lastMsb + 1
                lsb = randomLsb()
            } else {
                msb = lastMsb
                lsb = next
            }
        } else {
            msb = ((timestamp and 0xFFFFFFFFFFFFL) shl 16) or 0x7000L or (random.nextLong() and 0x0FFFL)
            lsb = randomLsb()
        }

        lastTimestamp = timestamp
        lastMsb = msb
        lastLsb = lsb
        UUID(msb, lsb)
    }

    private fun randomLsb(): Long = (random.nextLong() and 0x3FFFFFFFFFFFFFFFL) or Long.MIN_VALUE
}
