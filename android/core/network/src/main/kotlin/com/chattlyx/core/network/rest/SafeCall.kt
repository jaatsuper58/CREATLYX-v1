package com.chattlyx.core.network.rest

import com.chattlyx.core.common.error.ChattlyError
import com.chattlyx.core.common.result.Result
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.Response
import timber.log.Timber

private val problemJson = Json { ignoreUnknownKeys = true }

/**
 * Executes an API call and maps failures to the client error taxonomy
 * (master spec Section 6.7): 401 -> Auth, 429 -> RateLimited (Retry-After),
 * other non-2xx -> Server(code), IOException -> Network.
 */
suspend fun <T> safeCall(block: suspend () -> Response<T>): Result<T> = try {
    val response = block()
    if (response.isSuccessful) {
        Result.success(response.body() as T)
    } else {
        Result.failure(response.toChattlyError())
    }
} catch (e: java.io.IOException) {
    Result.failure(ChattlyError.Network(e))
} catch (e: kotlinx.serialization.SerializationException) {
    Result.failure(ChattlyError.Unknown(e))
} catch (e: Exception) {
    Result.failure(ChattlyError.Unknown(e))
}

private fun <T> Response<T>.toChattlyError(): ChattlyError {
    val status = code()
    if (status == 401) return ChattlyError.Auth
    if (status == 429) {
        val retryAfterSeconds = headers()["Retry-After"]?.toLongOrNull()
        return ChattlyError.RateLimited(retryAfterSeconds?.times(1000))
    }

    val code = try {
        val body = errorBody()?.string()
        body?.let { problemJson.parseToJsonElement(it).jsonObject["code"]?.jsonPrimitive?.content }
    } catch (e: Exception) {
        Timber.w(e, "Unparseable problem body (body not logged)")
        null
    }
    return ChattlyError.Server(code ?: "server/unknown", status)
}
