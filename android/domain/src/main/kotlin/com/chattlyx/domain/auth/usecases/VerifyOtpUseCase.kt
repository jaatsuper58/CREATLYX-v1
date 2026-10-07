package com.chattlyx.domain.auth.usecases

import com.chattlyx.core.common.error.ChattlyError
import com.chattlyx.core.common.result.Result
import com.chattlyx.domain.auth.AuthRepository
import com.chattlyx.domain.auth.AuthSession
import javax.inject.Inject

/** AUTH-03: verifies the 6-digit code and establishes the session. */
class VerifyOtpUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {

    suspend operator fun invoke(e164: String, code: String, deviceName: String): Result<AuthSession> {
        if (code.length != CODE_LENGTH || !code.all(Char::isDigit)) {
            return Result.failure(
                ChattlyError.Validation(field = "code", messageKey = "validation_otp_invalid"),
            )
        }
        return authRepository.verifyOtp(e164, code, deviceName)
    }

    companion object {
        const val CODE_LENGTH = 6
    }
}
