package com.chattlyx.domain.auth.usecases

import com.chattlyx.core.common.result.Result
import com.chattlyx.domain.auth.AuthRepository
import javax.inject.Inject

/** AUTH-10: hard step — deletes the account server-side and signs out. */
class DeleteAccountUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(): Result<Unit> = authRepository.deleteAccount()
}

/** Registration gate used by app navigation (AUTH-01). */
class HasActiveSessionUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(): Boolean = authRepository.hasActiveSession()
}

/** Sign out locally without account deletion. */
class SignOutUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke() = authRepository.signOut()
}
