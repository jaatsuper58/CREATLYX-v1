package com.chattlyx.core.crypto

import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * BKP-01/02 local backup cipher (JCA only, no custom primitives), the shipped
 * implementation of the [BackupCipher] contract in `Ciphers.kt`:
 *
 * - The key is derived from a user passphrase with PBKDF2-HMAC-SHA256
 *   (600k iterations per OWASP guidance) so the backup file is meaningful
 *   off-device without ever exporting Keystore material. The contract names
 *   Argon2id; PBKDF2 is used because the Argon2 provider is not yet in the
 *   dependency catalog and we never fabricate coordinates. The blob carries
 *   a version tag (`CHXBAK1`) so the KDF can be upgraded later.
 * - Payload is a single AES-256-GCM pass: confidentiality + integrity, tag
 *   verification fails closed on tamper or wrong passphrase.
 *
 * Layout: `"CHXBAK1"` | salt(16) | nonce(12) | ciphertext(+16-byte tag).
 */
object JcaBackupCipher : BackupCipher {

    const val MAGIC = "CHXBAK1"
    const val SALT_BYTES = 16
    const val NONCE_BYTES = 12
    const val PBKDF2_ITERATIONS = 600_000
    private const val KEY_BYTES = 32
    private const val GCM_TAG_BITS = 128
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    /** Passphrase did not match (GCM tag verification failed). */
    class WrongPassphraseException : Exception("backup passphrase mismatch")

    override suspend fun encryptBackup(plaintext: ByteArray, passphrase: CharArray): ByteArray =
        encrypt(plaintext, passphrase)

    override suspend fun decryptBackup(ciphertext: ByteArray, passphrase: CharArray): ByteArray =
        decrypt(ciphertext, passphrase)

    /** Encrypts [plaintext] for [passphrase]; returns the full backup blob. */
    fun encrypt(plaintext: ByteArray, passphrase: CharArray): ByteArray {
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(GCM_TAG_BITS, nonce))
        }
        val ciphertext = cipher.doFinal(plaintext)
        return MAGIC.toByteArray(Charsets.US_ASCII) + salt + nonce + ciphertext
    }

    /**
     * Decrypts a blob produced by [encrypt].
     * @throws WrongPassphraseException on passphrase mismatch,
     * @throws IllegalArgumentException on malformed/corrupt blobs.
     */
    fun decrypt(blob: ByteArray, passphrase: CharArray): ByteArray {
        val magic = MAGIC.toByteArray(Charsets.US_ASCII)
        val header = SALT_BYTES + NONCE_BYTES + magic.size
        if (blob.size < header + GCM_TAG_BITS / 8) {
            throw IllegalArgumentException("backup blob too short")
        }
        if (!blob.copyOfRange(0, magic.size).contentEquals(magic)) {
            throw IllegalArgumentException("not a ChattlyX backup")
        }
        val salt = blob.copyOfRange(magic.size, magic.size + SALT_BYTES)
        val nonce = blob.copyOfRange(magic.size + SALT_BYTES, header)
        val ciphertext = blob.copyOfRange(header, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(GCM_TAG_BITS, nonce))
        }
        return try {
            cipher.doFinal(ciphertext)
        } catch (e: AEADBadTagException) {
            throw WrongPassphraseException()
        }
    }

    internal fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_BYTES * 8)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }
}
