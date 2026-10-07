package com.chattlyx.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.chattlyx.core.common.result.Result
import com.chattlyx.core.common.result.map
import com.chattlyx.core.network.rest.ChattlyxServiceApi
import com.chattlyx.core.network.rest.ProfileDto
import com.chattlyx.core.network.rest.ProfileUpdateDto
import com.chattlyx.core.network.rest.safeCall
import com.chattlyx.domain.auth.AvatarImageSource
import com.chattlyx.domain.auth.Profile
import com.chattlyx.domain.auth.ProfileRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/** AUTH-04 profile repository over REST; encrypts avatars before upload. */
@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val api: ChattlyxServiceApi,
    private val avatarEncryptor: AvatarEncryptor,
    private val avatarKeyStore: AvatarKeyStore,
) : ProfileRepository {

    override suspend fun getProfile(): Result<Profile> =
        safeCall { api.getProfile() }.map { it.toDomain() }

    override suspend fun updateProfile(
        displayName: String,
        username: String?,
        about: String,
        avatarBlobId: String?,
    ): Result<Profile> = safeCall {
        api.updateProfile(ProfileUpdateDto(displayName, username, about, avatarBlobId))
    }.map { it.toDomain() }

    override suspend fun uploadAvatar(rawImage: ByteArray): Result<String> {
        val encrypted = avatarEncryptor.encrypt(rawImage)
        val result = safeCall {
            api.uploadAvatar(
                encrypted.ciphertext.toRequestBody("application/octet-stream".toMediaType()),
            )
        }
        return when (result) {
            is Result.Success -> {
                avatarKeyStore.save(result.value.blobId, encrypted.wrappedKey)
                Result.success(result.value.blobId)
            }

            is Result.Failure -> result
        }
    }

    private fun ProfileDto.toDomain() = Profile(
        accountId = accountId,
        displayName = displayName,
        username = username,
        about = about,
        avatarBlobId = avatarBlobId,
    )
}


/** Persists per-avatar AES keys (wrapped by Keystore) keyed by blob id. */
@Singleton
class AvatarKeyStore @Inject constructor(
    @AuthDataStore private val dataStore: DataStore<Preferences>,
) {

    suspend fun save(blobId: String, wrappedKey: ByteArray) {
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("avatar_key_$blobId")] =
                java.util.Base64.getEncoder().encodeToString(wrappedKey)
        }
    }

    suspend fun load(blobId: String): ByteArray? = dataStore.data
        .map { it[stringPreferencesKey("avatar_key_$blobId")] }
        .first()
        ?.let { java.util.Base64.getDecoder().decode(it) }
}

/** Downloads avatar ciphertext and decrypts it with the stored key. */
@Singleton
class AvatarImageSourceImpl @Inject constructor(
    private val api: ChattlyxServiceApi,
    private val avatarEncryptor: AvatarEncryptor,
    private val avatarKeyStore: AvatarKeyStore,
    @com.chattlyx.core.common.dispatchers.Dispatcher(com.chattlyx.core.common.dispatchers.ChattlyxDispatcher.IO)
    private val dispatcher: kotlinx.coroutines.CoroutineDispatcher,
) : AvatarImageSource {

    override suspend fun fetchDecryptedAvatar(blobId: String): Result<ByteArray> {
        val response = safeCall { api.downloadAvatar(blobId) }
        if (response is Result.Failure) return response

        val body = (response as Result.Success).value
        val bytes = kotlinx.coroutines.withContext(dispatcher) { body.bytes() }
        val wrappedKey = avatarKeyStore.load(blobId)
            ?: return Result.failure(
                com.chattlyx.core.common.error.ChattlyError.Crypto.DecryptFailed,
            )

        return try {
            Result.success(avatarEncryptor.decrypt(bytes, wrappedKey))
        } catch (e: Exception) {
            Result.failure(com.chattlyx.core.common.error.ChattlyError.Crypto.DecryptFailed)
        }
    }
}
