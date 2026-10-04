package com.chattlyx.core.database

import android.content.Context
import com.chattlyx.core.crypto.KeystoreKeyWrapper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the SQLCipher passphrase lifecycle (Section 7.1). The passphrase is
 * random; at rest it is wrapped by the Keystore master key (StrongBox when
 * available) and stored in app-private storage — never in plaintext, never in
 * Auto Backup (BKP-04). The Room/SQLCipher wiring lands in Phase 1.
 */
@Singleton
class DatabaseKeyProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyWrapper: KeystoreKeyWrapper,
) {

    /** Returns the database passphrase, creating and wrapping it on first use. */
    fun passphrase(): ByteArray {
        val file = passphraseFile()
        if (file.exists()) {
            return keyWrapper.unwrap(file.readBytes())
        }

        val passphrase = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        file.parentFile?.mkdirs()
        file.writeBytes(keyWrapper.wrap(passphrase))
        return passphrase
    }

    /** Secure deletion on account deletion (AUTH-10). */
    fun destroy() {
        val file = passphraseFile()
        if (file.exists()) {
            file.writeBytes(ByteArray(file.length().toInt()))
            file.delete()
        }
    }

    private fun passphraseFile(): File = File(context.filesDir, PASSPHRASE_FILE)

    private companion object {
        const val PASSPHRASE_BYTES = 32
        const val PASSPHRASE_FILE = "db.passphrase"
    }
}
