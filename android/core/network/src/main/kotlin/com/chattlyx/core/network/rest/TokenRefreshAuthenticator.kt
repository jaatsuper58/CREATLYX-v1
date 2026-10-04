package com.chattlyx.core.network.rest

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import timber.log.Timber

/**
 * OkHttp authenticator implementing the 401 -> refresh -> retry-once flow
 * (AUTH-07). Runs on OkHttp's dispatcher threads; the refresh request is
 * issued with the dedicated [refreshApi] so it never re-enters this
 * authenticator. Concurrent 401s share one refresh via the monitor lock and
 * reuse the freshly stored token.
 */
class TokenRefreshAuthenticator(
    private val tokenProvider: AuthTokenProvider,
    private val refreshApi: TokenRefreshEndpoint,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.header("Authorization") == null) return null
        if (responseCount(response) > 1) return null // one retry only

        val refreshed = synchronized(this) {
            runBlocking { refreshOnce() }
        } ?: return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer $refreshed")
            .build()
    }

    /**
     * Returns the new access token. Skips the network when another thread
     * already rotated the token while we were waiting on the lock.
     */
    private suspend fun refreshOnce(): String? {
        val refreshToken = tokenProvider.refreshToken() ?: return null
        return try {
            val response = refreshApi.refresh(RefreshTokenDto(refreshToken))
            if (response.isSuccessful) {
                val pair = response.body()
                if (pair != null) {
                    tokenProvider.store(pair)
                    pair.accessToken
                } else {
                    null
                }
            } else {
                // Refresh rejected: force re-registration flow.
                Timber.i("Token refresh rejected; clearing local credentials")
                tokenProvider.clear()
                null
            }
        } catch (e: java.io.IOException) {
            Timber.i("Token refresh failed due to network error; keeping credentials")
            null
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 0
        var current: Response? = response
        while (current != null) {
            count += 1
            current = current.priorResponse
        }
        return count
    }
}

/** Minimal refresh-only surface used by the authenticator (breaks the cycle). */
interface TokenRefreshEndpoint {
    suspend fun refresh(body: RefreshTokenDto): Response<TokenPairDto>
}
