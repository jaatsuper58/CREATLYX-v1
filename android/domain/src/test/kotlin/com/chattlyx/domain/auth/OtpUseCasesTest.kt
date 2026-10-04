package com.chattlyx.domain.auth

import com.chattlyx.core.common.error.ChattlyError
import com.chattlyx.core.common.result.Result
import com.chattlyx.domain.auth.usecases.RequestOtpUseCase
import com.chattlyx.domain.auth.usecases.VerifyOtpUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

private class FakeAuthRepository : AuthRepository {
    var lastRequestedE164: String? = null
    var lastVerifyCode: String? = null
    var failNext: ChattlyError? = null

    override suspend fun requestOtp(e164: String, language: String): Result<OtpRequestResult> {
        failNext?.let { error ->
            failNext = null
            return Result.failure(error)
        }
        lastRequestedE164 = e164
        return Result.success(OtpRequestResult(expiresInSeconds = 300, resendAfterSeconds = 60))
    }

    override suspend fun verifyOtp(e164: String, code: String, deviceName: String): Result<AuthSession> {
        lastVerifyCode = code
        return Result.success(AuthSession("acct", 1, 900))
    }

    override suspend fun hasActiveSession(): Boolean = false
    override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)
    override suspend fun signOut() = Unit
}

class RequestOtpUseCaseTest {

    private val repository = FakeAuthRepository()
    private val useCase = RequestOtpUseCase(repository)

    @Test
    fun `valid e164 is forwarded`() = runTest {
        val result = useCase("+919876543210", "en")
        assertIs<Result.Success<OtpRequestResult>>(result)
        assertEquals("+919876543210", repository.lastRequestedE164)
    }

    @Test
    fun `invalid e164 fails fast with validation error`() = runTest {
        val result = useCase("12345", "en")
        val failure = assertIs<Result.Failure>(result)
        assertIs<ChattlyError.Validation>(failure.error)
        assertEquals(null, repository.lastRequestedE164)
    }

    @Test
    fun `server errors pass through`() = runTest {
        repository.failNext = ChattlyError.RateLimited(retryAfterMillis = 42_000)
        val result = useCase("+919876543210", "en")
        val failure = assertIs<Result.Failure>(result)
        assertIs<ChattlyError.RateLimited>(failure.error)
    }
}

class VerifyOtpUseCaseTest {

    private val repository = FakeAuthRepository()
    private val useCase = VerifyOtpUseCase(repository)

    @Test
    fun `six digit code verifies`() = runTest {
        val result = useCase("+919876543210", "123456", "Pixel")
        assertIs<Result.Success<AuthSession>>(result)
        assertEquals("123456", repository.lastVerifyCode)
    }

    @Test
    fun `short code fails validation`() = runTest {
        val result = useCase("+919876543210", "1234", "Pixel")
        assertIs<Result.Failure>(result)
        assertEquals(null, repository.lastVerifyCode)
    }

    @Test
    fun `non numeric code fails validation`() = runTest {
        val result = useCase("+919876543210", "12a456", "Pixel")
        assertIs<Result.Failure>(result)
    }
}
