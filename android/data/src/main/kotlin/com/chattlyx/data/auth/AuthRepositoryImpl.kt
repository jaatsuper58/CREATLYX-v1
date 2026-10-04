package com.chattlyx.data.auth

import com.chattlyx.core.common.result.Result
import com.chattlyx.core.common.result.map
import com.chattlyx.core.network.rest.ChattlyxServiceApi
import com.chattlyx.core.network.rest.OtpRequestDto
import com.chattlyx.core.network.rest.OtpVerifyDto
import com.chattlyx.core.network.rest.safeCall
import com.chattlyx.domain.auth.AuthRepository
import com.chattlyx.domain.auth.AuthSession
import com.chattlyx.domain.auth.OtpRequestResult
import javax.inject.Inject
import javax.inject.Singleton

/** Registration/session flows backed by the REST API + secure local store. */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: ChattlyxServiceApi,
    private val tokenStore: SecureTokenStore,
    private val deviceNameProvider: DeviceNameProvider,
) : AuthRepository {

    override suspend fun requestOtp(e164: String, language: String): Result<OtpRequestResult> =
        safeCall { api.requestOtp(OtpRequestDto(e164, language)) }.map {
            OtpRequestResult(it.expiresInSeconds, it.resendAfterSeconds)
        }

    override suspend fun verifyOtp(
        e164: String,
        code: String,
        deviceName: String,
    ): Result<AuthSession> {
        val result = safeCall {
            api.verifyOtp(OtpVerifyDto(e164, code, deviceName.ifBlank { deviceNameProvider.name() }))
        }
        if (result is Result.Failure) return result

        val pair = (result as Result.Success).value
        tokenStore.store(pair)
        return Result.success(
            AuthSession(pair.accountId, pair.deviceId, pair.accessTokenExpiresInSeconds),
        )
    }

    override suspend fun hasActiveSession(): Boolean =
        tokenStore.isRegistered() && tokenStore.accessToken() != null

    override suspend fun deleteAccount(): Result<Unit> {
        val result = safeCall { api.deleteAccount() }
        // Even when the server call fails with a network error, surface it;
        // only clear local state on confirmed server deletion.
        if (result is Result.Success) {
            tokenStore.clear()
        }
        return result.map { }
    }

    override suspend fun signOut() {
        tokenStore.clear()
    }
}

/** Human-readable name for this device (AUTH-07 device list). */
interface DeviceNameProvider {
    fun name(): String
}

/** Default provider: the device model as reported by the platform. */
class BuildModelDeviceNameProvider @Inject constructor() : DeviceNameProvider {
    override fun name(): String =
        "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".take(64)
}
