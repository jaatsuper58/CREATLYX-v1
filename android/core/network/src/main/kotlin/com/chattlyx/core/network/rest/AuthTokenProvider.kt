package com.chattlyx.core.network.rest

/**
 * Token storage contract consumed by the OkHttp layer. Implemented in the
 * data layer (Keystore-wrapped DataStore). Reads must never block on
 * network; implementations may decrypt local ciphertext.
 */
interface AuthTokenProvider {
    /** Current access token or null when signed out. */
    suspend fun accessToken(): String?

    /** Current refresh token or null when signed out. */
    suspend fun refreshToken(): String?

    /** Persists a fresh token pair (registration or rotation). */
    suspend fun store(tokens: TokenPairDto)

    /** Removes all credential material (sign out / AUTH-10). */
    suspend fun clear()
}
