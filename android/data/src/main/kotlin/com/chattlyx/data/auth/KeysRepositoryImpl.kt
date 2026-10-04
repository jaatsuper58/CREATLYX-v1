package com.chattlyx.data.auth

import com.chattlyx.core.common.result.Result
import com.chattlyx.core.common.result.map
import com.chattlyx.core.network.rest.ChattlyxServiceApi
import com.chattlyx.core.network.rest.PrekeyDto
import com.chattlyx.core.network.rest.SignedPrekeyDto
import com.chattlyx.core.network.rest.UploadKeysDto
import com.chattlyx.core.network.rest.safeCall
import com.chattlyx.domain.auth.KeyCounts
import com.chattlyx.domain.auth.KeysRepository
import com.chattlyx.domain.auth.OwnKeyBundle
import com.chattlyx.domain.auth.PeerKeyBundle
import javax.inject.Inject
import javax.inject.Singleton

/** AUTH-06 key-bundle distribution over REST. */
@Singleton
class KeysRepositoryImpl @Inject constructor(
    private val api: ChattlyxServiceApi,
) : KeysRepository {

    override suspend fun uploadKeyBundle(bundle: OwnKeyBundle): Result<KeyCounts> = safeCall {
        api.uploadKeys(
            UploadKeysDto(
                identityKey = bundle.identityKey,
                signedPrekey = SignedPrekeyDto(bundle.signedPrekeyId, bundle.signedPrekeyRecord),
                oneTimePrekeys = bundle.oneTimePrekeys.map { (id, record) -> PrekeyDto(id, record) },
                kyberPrekeys = bundle.kyberPrekeys.map { (id, record) -> PrekeyDto(id, record) },
            ),
        )
    }.map { KeyCounts(it.oneTimePrekeys, it.kyberPrekeys) }

    override suspend fun getKeyCounts(): Result<KeyCounts> =
        safeCall { api.keyCount() }.map { KeyCounts(it.oneTimePrekeys, it.kyberPrekeys) }

    override suspend fun fetchPeerBundle(accountId: String, deviceId: Long): Result<PeerKeyBundle> =
        safeCall { api.keyBundle(accountId, deviceId) }.map {
            PeerKeyBundle(
                accountId = it.accountId,
                deviceId = it.deviceId,
                identityKey = it.identityKey,
                signedPrekeyId = it.signedPrekey?.prekeyId,
                signedPrekeyRecord = it.signedPrekey?.record,
                oneTimePrekeyId = it.oneTimePrekey?.prekeyId,
                oneTimePrekeyRecord = it.oneTimePrekey?.record,
                kyberPrekeyId = it.kyberPrekey?.prekeyId,
                kyberPrekeyRecord = it.kyberPrekey?.record,
            )
        }
}
