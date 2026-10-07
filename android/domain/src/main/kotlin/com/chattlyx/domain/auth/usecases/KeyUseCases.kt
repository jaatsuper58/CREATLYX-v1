package com.chattlyx.domain.auth.usecases

import com.chattlyx.core.common.result.Result
import com.chattlyx.domain.auth.KeyCounts
import com.chattlyx.domain.auth.KeysRepository
import com.chattlyx.domain.auth.OwnKeyBundle
import com.chattlyx.domain.auth.PeerKeyBundle
import javax.inject.Inject

/** AUTH-06: uploads this device's public key bundle. */
class UploadKeyBundleUseCase @Inject constructor(
    private val keysRepository: KeysRepository,
) {
    suspend operator fun invoke(bundle: OwnKeyBundle): Result<KeyCounts> =
        keysRepository.uploadKeyBundle(bundle)
}

/** AUTH-06: checks how many one-time prekeys remain locally. */
class GetKeyCountsUseCase @Inject constructor(
    private val keysRepository: KeysRepository,
) {
    suspend operator fun invoke(): Result<KeyCounts> = keysRepository.getKeyCounts()
}

/** AUTH-06: fetches a peer device's bundle to establish a session. */
class FetchPeerBundleUseCase @Inject constructor(
    private val keysRepository: KeysRepository,
) {
    suspend operator fun invoke(accountId: String, deviceId: Long): Result<PeerKeyBundle> =
        keysRepository.fetchPeerBundle(accountId, deviceId)
}
