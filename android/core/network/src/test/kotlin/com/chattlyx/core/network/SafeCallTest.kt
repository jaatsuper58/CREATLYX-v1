package com.chattlyx.core.network

import com.chattlyx.core.common.error.ChattlyError
import com.chattlyx.core.common.result.Result
import com.chattlyx.core.network.rest.safeCall
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

private val JSON = "application/json".toMediaType()

class SafeCallTest {

    @Test
    fun `2xx returns body`() = runTest {
        val result = safeCall { Response.success("hello") }
        val success = assertIs<Result.Success<String>>(result)
        assertEquals("hello", success.value)
    }

    @Test
    fun `401 maps to Auth`() = runTest {
        val result = safeCall { Response.error<String>(401, "{}".toResponseBody(JSON)) }
        val failure = assertIs<Result.Failure>(result)
        assertIs<ChattlyError.Auth>(failure.error)
    }

    @Test
    fun `429 maps to RateLimited with retry after`() = runTest {
        val result = safeCall {
            okhttp3.Response.Builder()
                .request(okhttp3.Request.Builder().url("https://x.test").build())
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(429)
                .message("Too Many Requests")
                .header("Retry-After", "17")
                .body("{}".toResponseBody(JSON))
                .build()
                .let { Response.error<String>(okhttp3.ResponseBody.create(JSON, "{}"), it) }
        }
        val failure = assertIs<Result.Failure>(result)
        val rateLimited = assertIs<ChattlyError.RateLimited>(failure.error)
        assertEquals(17_000L, rateLimited.retryAfterMillis)
    }

    @Test
    fun `problem json code surfaces as Server error`() = runTest {
        val problem = """{"title":"x","status":409,"code":"profile/username-taken"}"""
        val result = safeCall { Response.error<String>(409, problem.toResponseBody(JSON)) }
        val failure = assertIs<Result.Failure>(result)
        val server = assertIs<ChattlyError.Server>(failure.error)
        assertEquals("profile/username-taken", server.code)
        assertEquals(409, server.httpStatus)
    }

    @Test
    fun `io exceptions map to Network`() = runTest {
        val result = safeCall<String> { throw java.net.SocketTimeoutException("timeout") }
        val failure = assertIs<Result.Failure>(result)
        assertIs<ChattlyError.Network>(failure.error)
    }
}
