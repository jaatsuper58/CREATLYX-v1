package com.chattlyx.core.common.id

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UuidV7Test {

    @Test
    fun `generated uuid carries version 7 and rfc4122 variant`() {
        val uuid = UuidV7.generate(nowMillis = 1_727_000_000_000L)
        assertEquals(7, uuid.version())
        assertEquals(2, uuid.variant())
    }

    @Test
    fun `timestamp is encoded in the high 48 bits`() {
        val now = 1_727_123_456_789L
        val uuid = UuidV7.generate(nowMillis = now)
        val decoded = uuid.mostSignificantBits ushr 16
        assertEquals(now, decoded)
    }

    @Test
    fun `rapid generation stays unique and monotonic`() {
        val fixedNow = 1_727_000_000_000L
        val ids = (0 until 10_000).map { UuidV7.generate(nowMillis = fixedNow) }
        assertEquals(ids.size, ids.toSet().size)

        val asStrings = ids.map { it.toString() }
        assertEquals(asStrings.sorted(), asStrings, "same-millisecond ids must order lexicographically")
        assertTrue(ids.all { it.version() == 7 })
    }

    @Test
    fun `later timestamps sort after earlier ones`() {
        val earlier = UuidV7.generate(nowMillis = 1_727_000_000_000L)
        val later = UuidV7.generate(nowMillis = 1_727_000_001_000L)
        assertTrue(earlier.toString() < later.toString())
    }
}
