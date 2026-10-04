package com.chattlyx.domain.auth.usecases

import com.chattlyx.core.common.result.Result
import com.chattlyx.domain.auth.DeviceInfo
import com.chattlyx.domain.auth.DeviceRepository
import javax.inject.Inject

/** AUTH-07: lists all active devices on the account. */
class GetDevicesUseCase @Inject constructor(
    private val deviceRepository: DeviceRepository,
) {
    suspend operator fun invoke(): Result<List<DeviceInfo>> = deviceRepository.getDevices()
}

/** AUTH-07: revokes a linked device (tokens die with it server-side). */
class RevokeDeviceUseCase @Inject constructor(
    private val deviceRepository: DeviceRepository,
) {
    suspend operator fun invoke(deviceId: Long): Result<Unit> = deviceRepository.revokeDevice(deviceId)
}
