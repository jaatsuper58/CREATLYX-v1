package com.chattlyx.core.network

import com.chattlyx.core.network.rest.AuthTokenProvider
import com.chattlyx.core.network.rest.RefreshTokenDto
import com.chattlyx.core.network.rest.TokenPairDto
import com.chattlyx.core.network.rest.TokenRefreshAuthenticator
import com.chattlyx.core.network.rest.TokenRefreshEndpoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response as RetrofitResponse

private class FakeTokenProvider : AuthTokenProvider {
    var access: String? = "old-access"
    var refresh: String? = "old-refresh"
    var cleared = false

    override suspend fun accessToken(): String? = access
    override suspend fun refreshToken(): String? = refresh

    override suspend fun store(tokens: TokenPairDto) {
        access = tokens.accessToken
        refresh = tokens.refreshToken
    }

    override suspend fun clear() {
        access = null
        refresh = null
        cleared = true
    }
}

private class FakeRefreshEndpoint : TokenRefreshEndpoint {
    var calls = 0
    var nextResponse: RetrofitResponse<TokenPairDto>? = null

    override suspend fun refresh(body: RefreshTokenDto): RetrofitResponse<TokenPairDto> {
        calls += 1
        return nextResponse ?: RetrofitResponse.error(401, "{}".toResponseBody())
    }
}

class TokenRefreshAuthenticatorTest {

    private fun unauthorizedResponse(request: Request): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(401)
        .message("Unauthorized")
        .body("{}".toResponseBody())
        .build()

    private val request = Request.Builder()
        .url("https://api.chattlyx.com/v1/profile")
        .header("Authorization", "Bearer old-access")
        .build()

    @Test
    fun `refresh success retries with new token`() {
        val provider = FakeTokenProvider()
        val endpoint = FakeRefreshEndpoint().apply {
            nextResponse = RetrofitResponse.success(
                TokenPairDto("new-access", "new-refresh", 900, "acct", 1),
            )
        }
        val authenticator = TokenRefreshAuthenticator(provider, endpoint)

        val retried = authenticator.authenticate(null, unauthorizedResponse(request))

        assertEquals(1, endpoint.calls)
        assertEquals("Bearer new-access", retried?.header("Authorization"))
    }

    @Test
    fun `refresh failure clears credentials and gives up`() {
        val provider = FakeTokenProvider()
        val endpoint = FakeRefreshEndpoint()
        val authenticator = TokenRefreshAuthenticator(provider, endpoint)

        val retried = authenticator.authenticate(null, unauthorizedResponse(request))

        assertNull(retried)
        assertTrue(provider.cleared)
    }

    @Test
    fun `no auth header means nothing to retry`() {
        val provider = FakeTokenProvider()
        val endpoint = FakeRefreshEndpoint()
        val authenticator = TokenRefreshAuthenticator(provider, endpoint)

        val bare = Request.Builder().url("https://api.chattlyx.com/v1/config").build()
        val retried = authenticator.authenticate(null, unauthorizedResponse(bare))

        assertNull(retried)
        assertEquals(0, endpoint.calls)
    }

    @Test
    fun `already-retried requests are not retried again`() {
        val provider = FakeTokenProvider()
        val endpoint = FakeRefreshEndpoint().apply {
            nextResponse = RetrofitResponse.success(
                TokenPairDto("new-access", "new-refresh", 900, "acct", 1),
            )
        }
        val authenticator = TokenRefreshAuthenticator(provider, endpoint)

        // OkHttp requires prior responses to be body-less.
        val first = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .build()
        val second = unauthorizedResponse(
            request.newBuilder().header("Authorization", "Bearer new-access").build(),
        ).newBuilder().priorResponse(first).build()

        val retried = authenticator.authenticate(null, second)
        assertNull(retried)
    }
}
