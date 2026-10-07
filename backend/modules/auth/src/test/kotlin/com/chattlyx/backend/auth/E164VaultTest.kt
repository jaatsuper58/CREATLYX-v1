package com.chattlyx.backend.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class E164VaultTest {

    private val vault = E164Vault(ByteArray(32) { (it + 7).toByte() })

    @Test
    fun `encrypt decrypt roundtrip`() {
        val encrypted = vault.encrypt("+919876543210")
        assertEquals("+919876543210", vault.decrypt(encrypted))
    }

    @Test
    fun `ciphertext differs from plaintext`() {
        val encrypted = vault.encrypt("+919876543210")
        assertNotEquals("+919876543210", String(encrypted))
    }

    @Test
    fun `peppered hash is stable and pepper-sensitive`() {
        val a = vault.hash("+919876543210", "pepper-1")
        val b = vault.hash("+919876543210", "pepper-1")
        val c = vault.hash("+919876543210", "pepper-2")
        assertEquals(a, b)
        assertNotEquals(a, c)
    }
}
