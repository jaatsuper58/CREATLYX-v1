package com.chattlyx.core.crypto

import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** BKP-01/02: backup blob format and passphrase-derived AES-GCM behaviour. */
class BackupCipherTest {

    @Test
    fun `roundtrip restores the original payload`() {
        val plaintext = Random(11).nextBytes(4096)

        val blob = BackupCipher.encrypt(plaintext, "correct horse battery staple")
        val decrypted = BackupCipher.decrypt(blob, "correct horse battery staple")

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `wrong passphrase fails closed`() {
        val blob = BackupCipher.encrypt("secret conversations".toByteArray(), "right-passphrase")

        assertThrows(BackupCipher.WrongPassphraseException::class.java) {
            BackupCipher.decrypt(blob, "wrong-passphrase")
        }
    }

    @Test
    fun `tampered ciphertext fails authentication`() {
        val blob = BackupCipher.encrypt("secret conversations".toByteArray(), "some-passphrase")
        blob[blob.size - 1] = (blob[blob.size - 1] + 1).toByte()

        assertThrows(BackupCipher.WrongPassphraseException::class.java) {
            BackupCipher.decrypt(blob, "some-passphrase")
        }
    }

    @Test
    fun `missing magic is rejected before any key derivation`() {
        val blob = BackupCipher.encrypt("payload".toByteArray(), "any-passphrase")
        blob[0] = 'X'.code.toByte()

        assertThrows(IllegalArgumentException::class.java) {
            BackupCipher.decrypt(blob, "any-passphrase")
        }
    }

    @Test
    fun `truncated blob is rejected`() {
        val blob = BackupCipher.encrypt("payload".toByteArray(), "any-passphrase")

        assertThrows(IllegalArgumentException::class.java) {
            BackupCipher.decrypt(blob.copyOfRange(0, 8), "any-passphrase")
        }
    }
}
