package com.chattlyx.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chattlyx.core.common.dispatchers.ChattlyxDispatcher
import com.chattlyx.core.common.dispatchers.Dispatcher
import com.chattlyx.core.common.error.ChattlyError
import com.chattlyx.core.common.result.Result
import com.chattlyx.domain.auth.usecases.RequestOtpUseCase
import com.chattlyx.domain.auth.usecases.VerifyOtpUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** OTP entry state (AUTH-02/03). */
data class OtpState(
    val code: String = "",
    val verifying: Boolean = false,
    val requestingOtp: Boolean = false,
    val resendAvailableInSeconds: Int = 0,
    val codeExpiresInSeconds: Int = 0,
    val errorMessageRes: Int? = null,
    val verified: Boolean = false,
)

@HiltViewModel
class OtpViewModel @Inject constructor(
    private val requestOtpUseCase: RequestOtpUseCase,
    private val verifyOtpUseCase: VerifyOtpUseCase,
    @Dispatcher(ChattlyxDispatcher.DEFAULT) private val dispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(OtpState())
    val state: StateFlow<OtpState> = _state.asStateFlow()

    private var countdownJob: Job? = null

    /** AUTH-02: sends the OTP; call once on entry, again via [resend]. */
    fun requestOtp(e164: String) {
        viewModelScope.launch(dispatcher) {
            _state.update { it.copy(requestingOtp = true, errorMessageRes = null) }
            when (val result = requestOtpUseCase(e164, Locale.getDefault().language)) {
                is Result.Success -> {
                    _state.update {
                        it.copy(
                            requestingOtp = false,
                            resendAvailableInSeconds = result.value.resendAfterSeconds,
                            codeExpiresInSeconds = result.value.expiresInSeconds,
                        )
                    }
                    startCountdown()
                }

                is Result.Failure -> {
                    _state.update {
                        it.copy(requestingOtp = false, errorMessageRes = result.error.toMessageRes())
                    }
                }
            }
        }
    }

    fun onCodeChange(code: String) {
        _state.update { it.copy(code = code.filter(Char::isDigit).take(6), errorMessageRes = null) }
    }

    /** AUTH-03: verifies; surfaces localised failures inline. */
    fun verify(e164: String, deviceName: String) {
        val code = _state.value.code
        if (code.length < 6 || _state.value.verifying) return

        viewModelScope.launch(dispatcher) {
            _state.update { it.copy(verifying = true, errorMessageRes = null) }
            when (val result = verifyOtpUseCase(e164, code, deviceName)) {
                is Result.Success -> _state.update { it.copy(verifying = false, verified = true) }
                is Result.Failure -> _state.update {
                    it.copy(verifying = false, errorMessageRes = result.error.toMessageRes())
                }
            }
        }
    }

    fun resend(e164: String) {
        if (_state.value.resendAvailableInSeconds > 0) return
        requestOtp(e164)
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch(dispatcher) {
            while (_state.value.resendAvailableInSeconds > 0) {
                delay(1_000)
                _state.update {
                    it.copy(
                        resendAvailableInSeconds = (it.resendAvailableInSeconds - 1).coerceAtLeast(0),
                        codeExpiresInSeconds = (it.codeExpiresInSeconds - 1).coerceAtLeast(0),
                    )
                }
            }
        }
    }

    private fun ChattlyError.toMessageRes(): Int = when (this) {
        is ChattlyError.RateLimited -> R.string.onboarding_otp_rate_limited
        is ChattlyError.Network -> R.string.onboarding_error_network
        is ChattlyError.Server -> when (code) {
            "auth/otp-mismatch" -> R.string.onboarding_otp_wrong
            "auth/otp-expired" -> R.string.onboarding_otp_expired
            "auth/otp-consumed" -> R.string.onboarding_otp_expired
            "auth/otp-not-requested" -> R.string.onboarding_otp_expired
            else -> R.string.onboarding_error_server
        }

        is ChattlyError.Validation -> R.string.onboarding_otp_wrong
        else -> R.string.onboarding_error_server
    }
}
