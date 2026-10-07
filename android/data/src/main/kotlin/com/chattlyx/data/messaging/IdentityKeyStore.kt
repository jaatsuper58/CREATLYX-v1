package com.chattlyx.data.messaging

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.chattlyx.core.crypto.KeystoreKeyWrapper
import com.chattlyx.data.auth.AuthDataStore
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.NamedParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Persistent identity key pair for this device. The private key is wrapped
 * by the Keystore master key before touching disk; the public half is what
 * Phase 1 uploaded under AUTH-06.
 *
 * LICENCE GATE (ADR-0002): production identity keys come from libsignal.
 * This JCA X25519 store (EC P-256 fallback below API 26) keeps the same
 * storage contract so the swap stays local.
 */
@Singleton
class IdentityKeyStore @Inject constructor(
    @AuthDataStore private val dataStore: DataStore<Preferences>,
    private val keyWrapper: KeystoreKeyWrapper,
) : IdentityKeys {

    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    @Volatile
    private var cached: KeyPair? = null

    /** Blocking is safe: called from coroutine dispatchers, never Main. */
    override fun keyPair(): KeyPair {
        cached?.let { return it }
        return runBlocking { loadOrCreate() }.also { cached = it }
    }

    fun publicKeyBase64(): String = encoder.encodeToString(keyPair().public.encoded)

    private suspend fun loadOrCreate(): KeyPair {
        val prefs = dataStore.data.first()
        val privateWrapped = prefs[KEY_PRIVATE_WRAPPED]
        val publicB64 = prefs[KEY_PUBLIC]
        val algorithm = prefs[KEY_ALGORITHM] ?: ALG_X25519

        if (privateWrapped != null && publicB64 != null) {
            val privateKeyBytes = keyWrapper.unwrap(decoder.decode(privateWrapped))
            val factory = KeyFactory.getInstance(keyFactoryAlgorithm(algorithm))
            val privateKey: PrivateKey = factory.generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
            val publicKey: PublicKey = factory.generatePublic(X509EncodedKeySpec(decoder.decode(publicB64)))
            return KeyPair(publicKey, privateKey)
        }

        val pair = generate()
        dataStore.edit { p ->
            p[KEY_PRIVATE_WRAPPED] = encoder.encodeToString(keyWrapper.wrap(pair.private.encoded))
            p[KEY_PUBLIC] = encoder.encodeToString(pair.public.encoded)
            p[KEY_ALGORITHM] = pair.public.algorithm
        }
        return pair
    }

    private fun generate(): KeyPair =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            KeyPairGenerator.getInstance("XDH").apply {
                initialize(NamedParameterSpec.X25519)
            }.generateKeyPair()
        } else {
            KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        }

    private fun keyFactoryAlgorithm(publicAlgorithm: String): String =
        when (publicAlgorithm) {
            "X25519", "XDH" -> "XDH"
            else -> "EC"
        }

    private companion object {
        val KEY_PRIVATE_WRAPPED = stringPreferencesKey("identity_private_wrapped")
        val KEY_PUBLIC = stringPreferencesKey("identity_public")
        val KEY_ALGORITHM = stringPreferencesKey("identity_algorithm")
        const val ALG_X25519 = "X25519"
    }
}
