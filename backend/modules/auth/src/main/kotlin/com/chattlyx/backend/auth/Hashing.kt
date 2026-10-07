package com.chattlyx.backend.auth

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Constant-time hashing helpers. No custom primitives — JCA only. */
object Hashing {

    private val secureRandom = SecureRandom()

    fun sha256Hex(input: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(input).toHexString()

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    /** 256-bit random token, base64url without padding. */
    fun randomToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun randomDigits(length: Int): String {
        val sb = StringBuilder(length)
        repeat(length) { sb.append(secureRandom.nextInt(10)) }
        return sb.toString()
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
}
