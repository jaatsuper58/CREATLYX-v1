package com.chattlyx.data.messaging

import java.security.KeyFactory
import java.security.KeyPair
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Placeholder session cipher for Phase 2 1:1 text (MSG-01).
 *
 * Scheme: static-static ECDH between device identity keys -> HKDF-SHA256 ->
 * AES-256-GCM per message (random 12-byte nonce). This is NOT the final
 * protocol: it has no forward secrecy, no ratchet and no PQ — libsignal's
 * PQXDH + Double Ratchet replaces this class wholesale once ADR-0002
 * clears (the server treats ciphertext as opaque, so no API changes).
 */
/** Abstraction so the cipher is unit-testable without Android Keystore. */
interface IdentityKeys {
    fun keyPair(): KeyPair
}

class SessionCipher(
    private val identityKeys: IdentityKeys,
) {

    /** Encrypts plaintext for a peer identified by their public identity key. */
    fun encrypt(peerPublicKeyB64: String, plaintext: ByteArray): ByteArray {
        val shared = sharedSecret(peerPublicKeyB64)
        val key = hkdf(shared, info = INFO_KEY)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.iv + cipher.doFinal(plaintext)
    }

    /** Decrypts a blob from a peer (same static-static shared secret). */
    fun decrypt(peerPublicKeyB64: String, blob: ByteArray): ByteArray {
        require(blob.size > IV_BYTES) { "ciphertext too short" }
        val shared = sharedSecret(peerPublicKeyB64)
        val key = hkdf(shared, info = INFO_KEY)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, blob, 0, IV_BYTES),
        )
        return cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
    }

    private fun sharedSecret(peerPublicKeyB64: String): ByteArray {
        val peerBytes = Base64.getDecoder().decode(peerPublicKeyB64)
        val own = identityKeys.keyPair()
        val algorithm = own.public.algorithm
        val factoryName = if (algorithm == "X25519" || algorithm == "XDH") "XDH" else "EC"

        val peerKey: PublicKey = KeyFactory.getInstance(factoryName)
            .generatePublic(X509EncodedKeySpec(peerBytes))

        val agreementName = if (factoryName == "XDH") "X25519" else "ECDH"
        val agreement = KeyAgreement.getInstance(agreementName)
        agreement.init(own.private)
        agreement.doPhase(peerKey, true)
        return agreement.generateSecret()
    }

    /** Minimal HKDF-SHA256 (extract + single-block expand), RFC 5869. */
    private fun hkdf(ikm: ByteArray, info: String): ByteArray {
        val salt = ByteArray(32)
        val prk = hmac(salt, ikm)
        return hmac(prk, info.encodeToByteArray() + byteArrayOf(1))
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val INFO_KEY = "chattlyx.phase2.session.v1"
    }
}
