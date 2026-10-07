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
        val passphrase = "correct horse battery staple".toCharArray()

        val blob = JcaBackupCipher.encrypt(plaintext, passphrase)
        val decrypted = JcaBackupCipher.decrypt(blob, passphrase.copyOf())

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `wrong passphrase fails closed`() {
        val blob = JcaBackupCipher.encrypt(
            "secret conversations".toByteArray(),
            "right-passphrase".toCharArray(),
        )

        assertThrows(JcaBackupCipher.WrongPassphraseException::class.java) {
            JcaBackupCipher.decrypt(blob, "wrong-passphrase".toCharArray())
        }
    }

    @Test
    fun `tampered ciphertext fails authentication`() {
        val blob = JcaBackupCipher.encrypt(
            "secret conversations".toByteArray(),
            "some-passphrase".toCharArray(),
        )
        blob[blob.size - 1] = (blob[blob.size - 1] + 1).toByte()

        assertThrows(JcaBackupCipher.WrongPassphraseException::class.java) {
            JcaBackupCipher.decrypt(blob, "some-passphrase".toCharArray())
        }
    }

    @Test
    fun `missing magic is rejected before any key derivation`() {
        val blob = JcaBackupCipher.encrypt("payload".toByteArray(), "any-passphrase".toCharArray())
        blob[0] = 'X'.code.toByte()

        assertThrows(IllegalArgumentException::class.java) {
            JcaBackupCipher.decrypt(blob, "any-passphrase".toCharArray())
        }
    }

    @Test
    fun `truncated blob is rejected`() {
        val blob = JcaBackupCipher.encrypt("payload".toByteArray(), "any-passphrase".toCharArray())

        assertThrows(IllegalArgumentException::class.java) {
            JcaBackupCipher.decrypt(blob.copyOfRange(0, 8), "any-passphrase".toCharArray())
        }
    }
}
