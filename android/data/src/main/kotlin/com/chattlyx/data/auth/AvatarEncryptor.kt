package com.chattlyx.data.auth

import com.chattlyx.core.crypto.KeystoreKeyWrapper
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Avatar payload encryption (Phase 1, Section 9.4): AES-256-GCM with a
 * random per-avatar key. The server stores ciphertext only; the key stays
 * client-side (wrapped by the Keystore master key) until profile payloads
 * can carry it inside the E2EE channel (contact system, Phase 2+).
 */
@Singleton
class AvatarEncryptor @Inject constructor(
    private val keystore: KeystoreKeyWrapper,
) {

    data class EncryptedAvatar(
        val ciphertext: ByteArray,
        /** AES key wrapped with the Keystore master key. */
        val wrappedKey: ByteArray,
    )

    fun encrypt(plaintext: ByteArray): EncryptedAvatar {
        val key = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        val blob = cipher.iv + cipher.doFinal(plaintext)
        return EncryptedAvatar(ciphertext = blob, wrappedKey = keystore.wrap(key))
    }

    fun decrypt(ciphertext: ByteArray, wrappedKey: ByteArray): ByteArray {
        val key = keystore.unwrap(wrappedKey)
        require(ciphertext.size > IV_BYTES) { "avatar ciphertext too short" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, ciphertext, 0, IV_BYTES),
        )
        return cipher.doFinal(ciphertext, IV_BYTES, ciphertext.size - IV_BYTES)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BYTES = 32
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
