package com.chattlyx.backend.auth

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JwtServiceTest {

    private var now = 1_727_000_000_000L
    private val service = JwtService("test-secret-0123456789-0123456789".encodeToByteArray()) { now }
    private val accountId = UUID.randomUUID()

    @Test
    fun `signed token verifies with claims`() {
        val token = service.sign(accountId, deviceId = 3, ttlMillis = 900_000)
        val claims = assertNotNull(service.verify(token))
        assertEquals(accountId.toString(), claims.sub)
        assertEquals(3L, claims.dev)
    }

    @Test
    fun `tampered token is rejected`() {
        val token = service.sign(accountId, 1, 900_000)
        val tampered = token.dropLast(2) + "aa"
        assertNull(service.verify(tampered))
    }

    @Test
    fun `expired token is rejected`() {
        val token = service.sign(accountId, 1, ttlMillis = 60_000)
        now += 120_000
        assertNull(service.verify(token))
    }

    @Test
    fun `tokens from another secret are rejected`() {
        val other = JwtService("different-secret-aaaaaaaaaaaaaaaa".encodeToByteArray()) { now }
        val foreign = other.sign(accountId, 1, 900_000)
        assertNull(service.verify(foreign))
    }

    @Test
    fun `garbage input is rejected`() {
        assertNull(service.verify("not-a-jwt"))
        assertNull(service.verify("a.b"))
        assertNull(service.verify(""))
    }
}
