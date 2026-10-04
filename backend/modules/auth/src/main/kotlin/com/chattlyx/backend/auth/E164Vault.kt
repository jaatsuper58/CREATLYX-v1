package com.chattlyx.backend.auth

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts the E.164 value at rest (kept only because OTP delivery requires
 * it, Section 9.4). Peppered hashes are the primary lookup; this ciphertext
 * is decryptable only with the server key from the secret manager.
 */
class E164Vault(keyBytes: ByteArray) {

    private val key = SecretKeySpec(keyBytes, "AES")

    init {
        require(keyBytes.size == 32) { "CHATTLYX_E164_KEY must be 32 bytes" }
    }

    fun encrypt(e164: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.iv + cipher.doFinal(e164.encodeToByteArray())
    }

    fun decrypt(blob: ByteArray): String {
        require(blob.size > 12) { "ciphertext too short" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, blob.copyOfRange(0, 12)))
        return String(cipher.doFinal(blob, 12, blob.size - 12))
    }

    /** Peppered lookup hash for a number. */
    fun hash(e164: String, pepper: String): String =
        Hashing.sha256Hex(Hashing.hmacSha256(pepper.encodeToByteArray(), e164.encodeToByteArray()))

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        fun fromBase64Key(base64: String): E164Vault =
            E164Vault(Base64.getDecoder().decode(base64))
    }
}
