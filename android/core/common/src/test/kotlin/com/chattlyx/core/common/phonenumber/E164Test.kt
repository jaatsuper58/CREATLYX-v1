package com.chattlyx.core.common.phonenumber

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class E164Test {

    @Test
    fun `full international number passes through after separator cleanup`() {
        assertEquals("+919876543210", E164.normalize("+91 98765 43210", "+91"))
        assertEquals("+919876543210", E164.normalize("(+91)-98765-43210", "+91"))
    }

    @Test
    fun `national number gains the region dial code`() {
        assertEquals("+919876543210", E164.normalize("9876543210", "+91"))
    }

    @Test
    fun `leading zero trunk prefix is dropped`() {
        assertEquals("+447911123456", E164.normalize("07911123456", "+44"))
    }

    @Test
    fun `double-zero international prefix becomes plus`() {
        assertEquals("+919876543210", E164.normalize("00919876543210", "+91"))
    }

    @Test
    fun `structurally invalid numbers are rejected`() {
        assertNull(E164.normalize("", "+91"))
        assertNull(E164.normalize("123", "+91"))
        assertNull(E164.normalize("abcdefghij", "+91"))
        assertNull(E164.normalize("+0123456789", "+91"))
    }

    @Test
    fun `isValid enforces digit length bounds`() {
        assertTrue(E164.isValid("+12345"))
        assertTrue(E164.isValid("+123456789012345"))
        assertFalse(E164.isValid("+1234"))
        assertFalse(E164.isValid("+1234567890123456"))
        assertFalse(E164.isValid("12345678"))
    }
}
