package com.chattlyx.feature.onboarding

import app.cash.turbine.test
import com.chattlyx.core.common.error.ChattlyError
import com.chattlyx.core.common.result.Result
import com.chattlyx.domain.auth.AuthSession
import com.chattlyx.domain.auth.OtpRequestResult
import com.chattlyx.domain.auth.usecases.RequestOtpUseCase
import com.chattlyx.domain.auth.usecases.VerifyOtpUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OtpViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val requestOtp = mockk<RequestOtpUseCase>()
    private val verifyOtp = mockk<VerifyOtpUseCase>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = OtpViewModel(requestOtp, verifyOtp, dispatcher)

    @Test
    fun `request success populates countdowns`() = runTest {
        coEvery { requestOtp(any(), any()) } returns
            Result.success(OtpRequestResult(expiresInSeconds = 300, resendAfterSeconds = 60))

        val vm = viewModel()
        vm.requestOtp("+919876543210")

        vm.state.test {
            val state = awaitItem()
            assertEquals(60, state.resendAvailableInSeconds)
            assertEquals(300, state.codeExpiresInSeconds)
            assertFalse(state.requestingOtp)
        }
    }

    @Test
    fun `rate limited request shows backoff message`() = runTest {
        coEvery { requestOtp(any(), any()) } returns
            Result.failure(ChattlyError.RateLimited(retryAfterMillis = 30_000))

        val vm = viewModel()
        vm.requestOtp("+919876543210")

        vm.state.test {
            val state = awaitItem()
            assertEquals(R.string.onboarding_otp_rate_limited, state.errorMessageRes)
        }
    }

    @Test
    fun `verify success flips verified flag`() = runTest {
        coEvery { verifyOtp(any(), any(), any()) } returns
            Result.success(AuthSession("acct", 1, 900))

        val vm = viewModel()
        vm.onCodeChange("111111")
        vm.verify("+919876543210", "Pixel")

        vm.state.test {
            val state = awaitItem()
            assertTrue(state.verified)
        }
    }

    @Test
    fun `wrong code shows mismatch message`() = runTest {
        coEvery { verifyOtp(any(), any(), any()) } returns
            Result.failure(ChattlyError.Server("auth/otp-mismatch", 401))

        val vm = viewModel()
        vm.onCodeChange("000000")
        vm.verify("+919876543210", "Pixel")

        vm.state.test {
            val state = awaitItem()
            assertEquals(R.string.onboarding_otp_wrong, state.errorMessageRes)
            assertFalse(state.verified)
        }
    }

    @Test
    fun `short codes never call the server`() = runTest {
        val vm = viewModel()
        vm.onCodeChange("123")
        vm.verify("+919876543210", "Pixel")

        vm.state.test {
            val state = awaitItem()
            assertFalse(state.verified)
            assertFalse(state.verifying)
        }
        io.mockk.coVerify(exactly = 0) { verifyOtp(any(), any(), any()) }
    }
}
