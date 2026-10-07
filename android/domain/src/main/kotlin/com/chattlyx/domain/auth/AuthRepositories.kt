package com.chattlyx.domain.auth

import com.chattlyx.core.common.result.Result

/** Registration, session state and account deletion. */
interface AuthRepository {

    /** AUTH-02/03: request an OTP for the given E.164 number. */
    suspend fun requestOtp(e164: String, language: String): Result<OtpRequestResult>

    /** AUTH-03: verify the code; on success establishes a session (AUTH-07). */
    suspend fun verifyOtp(e164: String, code: String, deviceName: String): Result<AuthSession>

    /** True once a session and device exist locally (registration gate). */
    suspend fun hasActiveSession(): Boolean

    /** AUTH-10: delete the account and clear all local credentials. */
    suspend fun deleteAccount(): Result<Unit>

    /** Clears local session state (sign out without server deletion). */
    suspend fun signOut()
}

/** Profile reads/writes (AUTH-04). */
interface ProfileRepository {
    suspend fun getProfile(): Result<Profile>
    suspend fun updateProfile(
        displayName: String,
        username: String?,
        about: String,
        avatarBlobId: String? = null,
    ): Result<Profile>

    /**
     * Encrypts and uploads a raw avatar image; returns the blob id. The key
     * never leaves the device (Section 9.4).
     */
    suspend fun uploadAvatar(rawImage: ByteArray): Result<String>
}

/** Reads avatar ciphertext from the server and decrypts it locally. */
interface AvatarImageSource {
    suspend fun fetchDecryptedAvatar(blobId: String): Result<ByteArray>
}

/** Device management (AUTH-07). */
interface DeviceRepository {
    suspend fun getDevices(): Result<List<DeviceInfo>>
    suspend fun revokeDevice(deviceId: Long): Result<Unit>
}

/** Key-bundle distribution (AUTH-06). */
interface KeysRepository {
    suspend fun uploadKeyBundle(bundle: OwnKeyBundle): Result<KeyCounts>
    suspend fun getKeyCounts(): Result<KeyCounts>
    suspend fun fetchPeerBundle(accountId: String, deviceId: Long): Result<PeerKeyBundle>
}

/** This device's public key material for upload. */
data class OwnKeyBundle(
    val identityKey: String,
    val signedPrekeyId: Int,
    val signedPrekeyRecord: String,
    val oneTimePrekeys: List<Pair<Int, String>>,
    val kyberPrekeys: List<Pair<Int, String>>,
)
