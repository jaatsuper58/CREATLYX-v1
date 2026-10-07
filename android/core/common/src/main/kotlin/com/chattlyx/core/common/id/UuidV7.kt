package com.chattlyx.core.common.id

import java.security.SecureRandom
import java.util.UUID

/**
 * UUIDv7 generator (RFC 9514): unix-ms timestamp in the high 48 bits, version
 * nibble 7, random fill elsewhere. Provides time-ordered, idempotent client
 * message ids (MSG-02) without coordination.
 *
 * Monotonicity within the same millisecond is preserved by incrementing the
 * random payload rather than re-rolling it.
 */
object UuidV7 {

    private val random = SecureRandom()

    @Volatile
    private var lastTimestamp: Long = 0L

    @Volatile
    private var lastMsb: Long = 0L

    @Volatile
    private var lastLsb: Long = 0L

    fun generate(nowMillis: Long = java.lang.System.currentTimeMillis()): UUID = synchronized(this) {
        val timestamp = if (nowMillis > lastTimestamp) nowMillis else lastTimestamp
        val msb: Long
        val lsb: Long

        if (timestamp == lastTimestamp) {
            // Same millisecond: increment the 62 random bits to stay monotonic.
            val low = lastLsb + 1
            if (low == 0L) {
                msb = lastMsb + 1
                lsb = randomLsb()
            } else {
                msb = lastMsb
                lsb = low
            }
        } else {
            msb = buildMsb(timestamp)
            lsb = randomLsb()
        }

        lastTimestamp = timestamp
        lastMsb = msb
        lastLsb = lsb
        UUID(msb, lsb)
    }

    private fun buildMsb(timestamp: Long): Long {
        var msb = (timestamp and 0xFFFFFFFFFFFFL) shl 16
        msb = msb or (0x7000L) // version 7
        msb = msb or (random.nextLong() and 0x0FFFL) // rand_a (12 bits)
        return msb
    }

    private fun randomLsb(): Long {
        val bits = random.nextLong()
        // variant 10xx (RFC 4122-compatible two most-significant bits)
        return (bits and 0x3FFFFFFFFFFFFFFFL) or Long.MIN_VALUE
    }
}
