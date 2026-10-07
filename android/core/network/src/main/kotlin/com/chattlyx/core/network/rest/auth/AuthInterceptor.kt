package com.chattlyx.core.network.rest.auth

import com.chattlyx.core.network.rest.AuthTokenProvider
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches `Authorization: Bearer <token>` to every request when a session
 * exists (AUTH-07). Runs on OkHttp dispatcher threads.
 */
class AuthInterceptor(private val tokenProvider: AuthTokenProvider) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = runBlocking { tokenProvider.accessToken() }
            ?: return chain.proceed(request)

        return chain.proceed(
            request.newBuilder().header("Authorization", "Bearer $token").build(),
        )
    }
}
