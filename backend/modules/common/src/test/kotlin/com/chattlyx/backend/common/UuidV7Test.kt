package com.chattlyx.backend.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UuidV7Test {

    @Test
    fun `version and variant bits are set correctly`() {
        val uuid = UuidV7.generate(nowMillis = 1_727_000_000_000L)
        assertEquals(7, uuid.version())
        assertEquals(2, uuid.variant())
    }

    @Test
    fun `rapid generation stays unique`() {
        val ids = (0 until 5_000).map { UuidV7.generate(nowMillis = 1_727_000_000_000L) }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.version() == 7 })
    }
}
