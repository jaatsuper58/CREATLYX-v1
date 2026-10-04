package com.chattlyx.core.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AttachmentCipherTest {

    private val chunkSize = 1024

    @Test
    fun `roundtrip preserves content for multi-chunk payloads`() {
        val plaintext = Random(7).nextBytes(chunkSize * 3 + 137)

        val encrypted = ByteArrayOutputStream()
        val result = AttachmentCipher.encrypt(
            input = ByteArrayInputStream(plaintext),
            output = encrypted,
            chunkSize = chunkSize,
        )

        val decrypted = ByteArrayOutputStream()
        AttachmentCipher.decrypt(
            input = ByteArrayInputStream(encrypted.toByteArray()),
            output = decrypted,
            key = result.key,
            nonce = result.nonce,
            chunkSize = chunkSize,
            expectedDigest = result.digest,
        )

        assertArrayEquals(plaintext, decrypted.toByteArray())
    }

    @Test
    fun `empty input produces empty output`() {
        val encrypted = ByteArrayOutputStream()
        val result = AttachmentCipher.encrypt(
            input = ByteArrayInputStream(ByteArray(0)),
            output = encrypted,
            chunkSize = chunkSize,
        )
        assertEquals(0L, result.ciphertextSize)

        val decrypted = ByteArrayOutputStream()
        AttachmentCipher.decrypt(
            input = ByteArrayInputStream(encrypted.toByteArray()),
            output = decrypted,
            key = result.key,
            nonce = result.nonce,
            chunkSize = chunkSize,
        )
        assertEquals(0, decrypted.size())
    }

    @Test
    fun `single chunk payload roundtrips`() {
        val plaintext = Random(3).nextBytes(10)
        val encrypted = ByteArrayOutputStream()
        val result = AttachmentCipher.encrypt(
            input = ByteArrayInputStream(plaintext),
            output = encrypted,
            chunkSize = chunkSize,
        )

        val decrypted = ByteArrayOutputStream()
        AttachmentCipher.decrypt(
            input = ByteArrayInputStream(encrypted.toByteArray()),
            output = decrypted,
            key = result.key,
            nonce = result.nonce,
            chunkSize = chunkSize,
            expectedDigest = result.digest,
        )
        assertArrayEquals(plaintext, decrypted.toByteArray())
    }

    @Test
    fun `tampered ciphertext is rejected`() {
        val plaintext = Random(11).nextBytes(chunkSize * 2)
        val encrypted = ByteArrayOutputStream()
        val result = AttachmentCipher.encrypt(
            input = ByteArrayInputStream(plaintext),
            output = encrypted,
            chunkSize = chunkSize,
        )

        val tampered = encrypted.toByteArray().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }

        assertThrows(SecurityException::class.java) {
            AttachmentCipher.decrypt(
                input = ByteArrayInputStream(tampered),
                output = ByteArrayOutputStream(),
                key = result.key,
                nonce = result.nonce,
                chunkSize = chunkSize,
                expectedDigest = result.digest,
            )
        }
    }

    @Test
    fun `wrong key is rejected`() {
        val plaintext = Random(13).nextBytes(chunkSize)
        val encrypted = ByteArrayOutputStream()
        val result = AttachmentCipher.encrypt(
            input = ByteArrayInputStream(plaintext),
            output = encrypted,
            chunkSize = chunkSize,
        )

        val wrongKey = result.key.copyOf().also { it[0] = (it[0] + 1).toByte() }
        assertThrows(Exception::class.java) {
            AttachmentCipher.decrypt(
                input = ByteArrayInputStream(encrypted.toByteArray()),
                output = ByteArrayOutputStream(),
                key = wrongKey,
                nonce = result.nonce,
                chunkSize = chunkSize,
            )
        }
    }

    @Test
    fun `random-access chunk decryption matches full decryption`() {
        val plaintext = Random(17).nextBytes(chunkSize * 4)
        val encrypted = ByteArrayOutputStream()
        val result = AttachmentCipher.encrypt(
            input = ByteArrayInputStream(plaintext),
            output = encrypted,
            chunkSize = chunkSize,
        )

        // Decrypt only chunk 2 (0-based) directly from the ciphertext stream.
        val encryptedChunkSize = chunkSize + 16
        val ciphertext = encrypted.toByteArray()
        val chunkBytes = ciphertext.copyOfRange(
            2 * encryptedChunkSize,
            minOf(3 * encryptedChunkSize, ciphertext.size),
        )

        val decrypted = ByteArrayOutputStream()
        AttachmentCipher.decrypt(
            input = ByteArrayInputStream(chunkBytes),
            output = decrypted,
            key = result.key,
            nonce = result.nonce,
            firstChunk = 2,
            chunkSize = chunkSize,
        )

        assertArrayEquals(
            plaintext.copyOfRange(2 * chunkSize, 3 * chunkSize),
            decrypted.toByteArray(),
        )
    }
}
