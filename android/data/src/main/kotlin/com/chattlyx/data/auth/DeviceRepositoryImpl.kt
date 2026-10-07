package com.chattlyx.data.auth

import com.chattlyx.core.common.result.Result
import com.chattlyx.core.common.result.map
import com.chattlyx.core.network.rest.ChattlyxServiceApi
import com.chattlyx.core.network.rest.safeCall
import com.chattlyx.domain.auth.DeviceInfo
import com.chattlyx.domain.auth.DeviceRepository
import javax.inject.Inject
import javax.inject.Singleton

/** AUTH-07 device management over REST. */
@Singleton
class DeviceRepositoryImpl @Inject constructor(
    private val api: ChattlyxServiceApi,
) : DeviceRepository {

    override suspend fun getDevices(): Result<List<DeviceInfo>> =
        safeCall { api.getDevices() }.map { list ->
            list.devices.map {
                DeviceInfo(
                    deviceId = it.deviceId,
                    name = it.name,
                    createdAt = it.createdAt,
                    lastSeenAt = it.lastSeenAt,
                    current = it.current,
                )
            }
        }

    override suspend fun revokeDevice(deviceId: Long): Result<Unit> =
        safeCall { api.revokeDevice(deviceId) }.map { }
}
