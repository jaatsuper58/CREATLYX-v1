package com.chattlyx.core.crypto

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Chunked attachment encryption (master spec Section 4.5 / 9.1):
 *
 * - One random 256-bit key per file; the key + digest travel inside the E2EE
 *   message, so the server only ever stores ciphertext (MED-*).
 * - AES-256-GCM per 1 MB chunk; chunk nonce = base nonce XOR chunk index,
 *   which keeps decryption random-access for resumable downloads (MED-04).
 * - SHA-256 digest over the whole ciphertext stream for end-to-end integrity.
 *
 * Implemented on JCA only — no engine dependency, fully unit-testable.
 */
object AttachmentCipher {

    const val DEFAULT_CHUNK_SIZE = 1024 * 1024
    const val KEY_BYTES = 32
    const val NONCE_BYTES = 12
    const val DIGEST_BYTES = 32

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val STREAM_BUFFER = 64 * 1024

    /** Everything a message needs to reference and later decrypt an upload. */
    data class EncryptionResult(
        val key: ByteArray,
        val nonce: ByteArray,
        val digest: ByteArray,
        val ciphertextSize: Long,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EncryptionResult) return false
            return key.contentEquals(other.key) &&
                nonce.contentEquals(other.nonce) &&
                digest.contentEquals(other.digest) &&
                ciphertextSize == other.ciphertextSize
        }

        override fun hashCode(): Int =
            key.contentHashCode() * 31 + nonce.contentHashCode() + digest.contentHashCode()
    }

    fun generateKey(random: SecureRandom = SecureRandom()): ByteArray =
        ByteArray(KEY_BYTES).also(random::nextBytes)

    fun generateNonce(random: SecureRandom = SecureRandom()): ByteArray =
        ByteArray(NONCE_BYTES).also(random::nextBytes)

    /**
     * Encrypts [input] chunk by chunk into [output]. Returns the key material
     * and digest to embed in the E2EE envelope.
     */
    fun encrypt(
        input: InputStream,
        output: OutputStream,
        key: ByteArray = generateKey(),
        nonce: ByteArray = generateNonce(),
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
    ): EncryptionResult {
        require(key.size == KEY_BYTES) { "key must be $KEY_BYTES bytes" }
        require(nonce.size == NONCE_BYTES) { "nonce must be $NONCE_BYTES bytes" }
        require(chunkSize > 0) { "chunkSize must be positive" }

        val keySpec = SecretKeySpec(key, "AES")
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(chunkSize)
        var chunkIndex = 0
        var totalOut = 0L

        while (true) {
            var filled = 0
            while (filled < chunkSize) {
                val read = input.read(buffer, filled, chunkSize - filled)
                if (read == -1) break
                filled += read
            }
            if (filled == 0) break

            val ciphertext = processChunk(
                mode = Cipher.ENCRYPT_MODE,
                keySpec = keySpec,
                nonce = nonce,
                chunkIndex = chunkIndex,
                data = buffer,
                length = filled,
            )
            output.write(ciphertext)
            digest.update(ciphertext)
            totalOut += ciphertext.size
            chunkIndex++
        }

        output.flush()
        return EncryptionResult(key, nonce, digest.digest(), totalOut)
    }

    /**
     * Decrypts a byte range of chunks. [firstChunk]/[lastChunk] enable
     * resumable downloads without re-fetching finished chunks (MED-04).
     *
     * When [lastChunk] is null the stream is read to EOF. When the final
     * chunk is included and [expectedDigest] is provided, the whole-stream
     * SHA-256 is verified before returning.
     */
    fun decrypt(
        input: InputStream,
        output: OutputStream,
        key: ByteArray,
        nonce: ByteArray,
        firstChunk: Int = 0,
        lastChunk: Int? = null,
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
        expectedDigest: ByteArray? = null,
    ) {
        require(key.size == KEY_BYTES) { "key must be $KEY_BYTES bytes" }
        require(nonce.size == NONCE_BYTES) { "nonce must be $NONCE_BYTES bytes" }

        val keySpec = SecretKeySpec(key, "AES")
        // Each plaintext chunk of C bytes becomes C + 16 bytes of ciphertext.
        val encryptedChunkSize = chunkSize + GCM_TAG_BITS / 8
        // Whole-stream verification only makes sense for a complete pass.
        val verifyDigest = expectedDigest != null && firstChunk == 0 && lastChunk == null
        val digest = if (verifyDigest) MessageDigest.getInstance("SHA-256") else null

        var chunkIndex = firstChunk
        val buffer = ByteArray(encryptedChunkSize)

        while (true) {
            if (lastChunk != null && chunkIndex > lastChunk) break

            var filled = 0
            while (filled < encryptedChunkSize) {
                val read = input.read(buffer, filled, encryptedChunkSize - filled)
                if (read == -1) break
                filled += read
            }
            if (filled == 0) break

            digest?.update(buffer, 0, filled)

            val plaintext = try {
                processChunk(
                    mode = Cipher.DECRYPT_MODE,
                    keySpec = keySpec,
                    nonce = nonce,
                    chunkIndex = chunkIndex,
                    data = buffer,
                    length = filled,
                )
            } catch (e: javax.crypto.AEADBadTagException) {
                throw SecurityException("attachment chunk failed authentication", e)
            }
            output.write(plaintext)
            chunkIndex++

            if (filled < encryptedChunkSize) break // final partial chunk
        }

        if (digest != null && expectedDigest != null) {
            val actual = digest.digest()
            if (!MessageDigest.isEqual(actual, expectedDigest)) {
                throw SecurityException("attachment digest mismatch")
            }
        }
        output.flush()
    }

    private fun processChunk(
        mode: Int,
        keySpec: SecretKeySpec,
        nonce: ByteArray,
        chunkIndex: Int,
        data: ByteArray,
        length: Int,
    ): ByteArray {
        val chunkNonce = nonce.copyOf().also { base ->
            // XOR the 12-byte base nonce with the chunk index (big-endian).
            var index = chunkIndex
            var byte = NONCE_BYTES - 1
            while (index != 0 && byte >= 0) {
                base[byte] = (base[byte].toInt() xor (index and 0xFF)).toByte()
                index = index ushr 8
                byte--
            }
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(mode, keySpec, GCMParameterSpec(GCM_TAG_BITS, chunkNonce))
        return cipher.doFinal(data, 0, length)
    }
}
