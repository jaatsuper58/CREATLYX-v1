package com.chattlyx.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.chattlyx.core.crypto.KeystoreKeyWrapper
import com.chattlyx.core.network.rest.AuthTokenProvider
import com.chattlyx.core.network.rest.TokenPairDto
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Persists session credentials encrypted at rest: each value is wrapped with
 * the Keystore master key (AES-256-GCM via [KeystoreKeyWrapper]) before it
 * reaches DataStore. Registration state (AUTH-01 gate) lives here too.
 */
@Singleton
class SecureTokenStore @Inject constructor(
    @AuthDataStore private val dataStore: DataStore<Preferences>,
    private val keystore: KeystoreKeyWrapper,
) : AuthTokenProvider {

    override suspend fun accessToken(): String? = readString(KEY_ACCESS_TOKEN)

    override suspend fun refreshToken(): String? = readString(KEY_REFRESH_TOKEN)

    suspend fun accountId(): String? = dataStore.data.map { it[KEY_ACCOUNT_ID] }.first()

    suspend fun deviceId(): Long? =
        dataStore.data.map { it[KEY_DEVICE_ID]?.toLongOrNull() }.first()

    suspend fun isRegistered(): Boolean =
        dataStore.data.map { it[KEY_REGISTERED] ?: false }.first()

    override suspend fun store(tokens: TokenPairDto) {
        dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = wrap(tokens.accessToken)
            prefs[KEY_REFRESH_TOKEN] = wrap(tokens.refreshToken)
            prefs[KEY_ACCOUNT_ID] = tokens.accountId
            prefs[KEY_DEVICE_ID] = tokens.deviceId.toString()
            prefs[KEY_REGISTERED] = true
        }
    }

    /** AUTH-10 / sign out: wipe everything locally. */
    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private suspend fun readString(key: Preferences.Key<String>): String? =
        dataStore.data
            .map { prefs -> prefs[key]?.let { runCatching { unwrap(it) }.getOrNull() } }
            .first()

    private fun wrap(value: String): String =
        Base64.getEncoder().encodeToString(keystore.wrap(value.encodeToByteArray()))

    private fun unwrap(encoded: String): String =
        String(keystore.unwrap(Base64.getDecoder().decode(encoded)))

    private companion object {
        val KEY_ACCESS_TOKEN = stringPreferencesKey("auth_access_token_wrapped")
        val KEY_REFRESH_TOKEN = stringPreferencesKey("auth_refresh_token_wrapped")
        val KEY_ACCOUNT_ID = stringPreferencesKey("auth_account_id")
        val KEY_DEVICE_ID = stringPreferencesKey("auth_device_id")
        val KEY_REGISTERED = booleanPreferencesKey("auth_registered")
    }
}
