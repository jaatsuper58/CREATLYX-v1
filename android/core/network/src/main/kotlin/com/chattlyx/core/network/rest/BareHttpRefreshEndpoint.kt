package com.chattlyx.core.network.rest

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import timber.log.Timber

/**
 * Refresh endpoint implemented on a plain OkHttpClient so the 401
 * authenticator never depends on the app's Retrofit instance (which would
 * create a client -> authenticator -> client cycle).
 */
class BareHttpRefreshEndpoint(
    private val httpClient: OkHttpClient,
    private val baseUrl: String,
) : TokenRefreshEndpoint {

    override suspend fun refresh(body: RefreshTokenDto): retrofit2.Response<TokenPairDto> {
        val jsonBody = ChattlyxJson.encodeToString(RefreshTokenDto.serializer(), body)
        val request = Request.Builder()
            .url(baseUrl + "v1/auth/token/refresh")
            .post(jsonBody.toRequestBody(JSON))
            .build()

        httpClient.newCall(request).execute().use { raw ->
            val text = raw.body?.string().orEmpty()
            return if (raw.isSuccessful) {
                val pair = try {
                    ChattlyxJson.decodeFromString(TokenPairDto.serializer(), text)
                } catch (e: Exception) {
                    Timber.w(e, "Unparseable refresh response")
                    return retrofit2.Response.error(raw.code, okhttp3.ResponseBody.create(JSON, text))
                }
                retrofit2.Response.success(pair)
            } else {
                retrofit2.Response.error(raw.code, okhttp3.ResponseBody.create(JSON, text))
            }
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
