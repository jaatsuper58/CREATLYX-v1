package com.chattlyx.domain.auth.usecases

import com.chattlyx.core.common.error.ChattlyError
import com.chattlyx.core.common.phonenumber.E164
import com.chattlyx.core.common.result.Result
import com.chattlyx.domain.auth.AuthRepository
import com.chattlyx.domain.auth.OtpRequestResult
import javax.inject.Inject

/** AUTH-02: validates the number locally, then requests an OTP. */
class RequestOtpUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {

    suspend operator fun invoke(e164: String, language: String): Result<OtpRequestResult> {
        if (!E164.isValid(e164)) {
            return Result.failure(
                ChattlyError.Validation(field = "phone", messageKey = "validation_phone_invalid"),
            )
        }
        return authRepository.requestOtp(e164, language)
    }
}
