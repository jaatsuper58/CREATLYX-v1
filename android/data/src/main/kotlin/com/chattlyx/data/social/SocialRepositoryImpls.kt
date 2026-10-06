package com.chattlyx.data.social

import com.chattlyx.core.common.dispatchers.ChattlyxDispatcher
import com.chattlyx.core.common.dispatchers.Dispatcher
import com.chattlyx.core.common.result.Result
import com.chattlyx.core.common.result.getOrElse
import com.chattlyx.core.common.result.map
import com.chattlyx.core.database.dao.BlockedPeerDao
import com.chattlyx.core.database.entity.BlockedPeerEntity
import com.chattlyx.core.network.rest.ChattlyxServiceApi
import com.chattlyx.core.network.rest.safeCall
import com.chattlyx.domain.social.BlockRepository
import com.chattlyx.domain.social.PresenceInfo
import com.chattlyx.domain.social.PresenceRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** STS presence: straight REST read-through. */
@Singleton
class PresenceRepositoryImpl @Inject constructor(
    private val api: ChattlyxServiceApi,
    @Dispatcher(ChattlyxDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : PresenceRepository {

    override suspend fun presenceFor(accountId: String): Result<PresenceInfo> =
        withContext(ioDispatcher) {
            safeCall { api.presence(accountId) }.map { dto ->
                PresenceInfo(
                    accountId = dto.accountId,
                    online = dto.online,
                    lastSeenMs = dto.lastSeenMs,
                )
            }
        }
}

/**
 * SAF block list: server source of truth + Room-backed cache (SAF hardening)
 * with an in-memory mirror so UI checks stay synchronous. On first read the
 * mirror hydrates from the local table — block enforcement therefore works
 * immediately after process restart, even before/offline of the next server
 * sync — then a best-effort refresh reconciles with the server.
 */
@Singleton
class BlockRepositoryImpl @Inject constructor(
    private val api: ChattlyxServiceApi,
    private val blockedPeerDao: BlockedPeerDao,
    @Dispatcher(ChattlyxDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : BlockRepository {

    private val cache = MutableStateFlow<List<String>>(emptyList())
    private var hydrated = false

    override fun observeBlocked(): Flow<List<String>> = cache.asStateFlow()

    override suspend fun refresh(): Result<Unit> = withContext(ioDispatcher) {
        if (!hydrated) {
            cache.value = blockedPeerDao.snapshot()
            hydrated = true
        }
        safeCall { api.blockList() }.map { dto ->
            blockedPeerDao.clear()
            blockedPeerDao.insertAll(
                dto.blockedAccountIds.map { id ->
                    BlockedPeerEntity(accountId = id, blockedAt = System.currentTimeMillis())
                },
            )
            cache.value = dto.blockedAccountIds
            Result.success(Unit)
        }.getOrElse { Result.failure(it) }
    }

    override suspend fun block(accountId: String): Result<Unit> = withContext(ioDispatcher) {
        safeCall { api.blockPeer(accountId) }.map {
            blockedPeerDao.insert(
                BlockedPeerEntity(accountId = accountId, blockedAt = System.currentTimeMillis()),
            )
            cache.value = (cache.value + accountId).distinct()
            Result.success(Unit)
        }.getOrElse { Result.failure(it) }
    }

    override suspend fun unblock(accountId: String): Result<Unit> = withContext(ioDispatcher) {
        safeCall { api.unblockPeer(accountId) }.map {
            blockedPeerDao.delete(accountId)
            cache.value = cache.value.filterNot { it == accountId }
            Result.success(Unit)
        }.getOrElse { Result.failure(it) }
    }

    override suspend fun isBlocked(accountId: String): Boolean {
        if (!hydrated) refresh()
        return accountId in cache.value
    }
}
