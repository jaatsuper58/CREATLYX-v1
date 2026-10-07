package com.chattlyx.core.crypto

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Wraps arbitrary secret material with an AES-256-GCM key held in Android
 * Keystore (StrongBox when present). Used to protect the SQLCipher
 * passphrase, Signal-store values and refresh-token blobs at rest.
 *
 * Wrapped format: [12-byte IV][ciphertext + 16-byte GCM tag].
 */
interface KeystoreKeyWrapper {
    fun wrap(secret: ByteArray): ByteArray
    fun unwrap(wrapped: ByteArray): ByteArray
    val usesStrongBox: Boolean
}

class KeystoreKeyWrapperImpl(
    private val context: Context,
    private val alias: String = DEFAULT_ALIAS,
) : KeystoreKeyWrapper {

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    override val usesStrongBox: Boolean by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
    }

    private fun getOrCreateKey(): SecretKey {
        keyStore.getKey(alias, null)?.let { return it as SecretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(true)

        if (usesStrongBox) {
            builder.setIsStrongBoxBacked(true)
        }

        generator.init(builder.build())
        return generator.generateKey()
    }

    override fun wrap(secret: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(secret)
        return iv + ciphertext
    }

    override fun unwrap(wrapped: ByteArray): ByteArray {
        require(wrapped.size > GCM_IV_BYTES) { "wrapped payload too short" }
        val iv = wrapped.copyOfRange(0, GCM_IV_BYTES)
        val ciphertext = wrapped.copyOfRange(GCM_IV_BYTES, wrapped.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        const val DEFAULT_ALIAS = "chattlyx_master_key"
    }
}
