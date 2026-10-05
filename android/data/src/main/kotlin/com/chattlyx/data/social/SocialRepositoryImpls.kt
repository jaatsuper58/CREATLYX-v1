package com.chattlyx.data.social

import com.chattlyx.core.common.dispatchers.ChattlyxDispatcher
import com.chattlyx.core.common.dispatchers.Dispatcher
import com.chattlyx.core.common.result.Result
import com.chattlyx.core.common.result.getOrElse
import com.chattlyx.core.common.result.map
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
 * SAF block list: server source of truth + in-memory cache so UI checks are
 * synchronous. The cache hydrates lazily on first read after process start.
 */
@Singleton
class BlockRepositoryImpl @Inject constructor(
    private val api: ChattlyxServiceApi,
    @Dispatcher(ChattlyxDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : BlockRepository {

    private val cache = MutableStateFlow<List<String>>(emptyList())
    private var hydrated = false

    override fun observeBlocked(): Flow<List<String>> = cache.asStateFlow()

    override suspend fun refresh(): Result<Unit> = withContext(ioDispatcher) {
        safeCall { api.blockList() }.map { dto ->
            cache.value = dto.blockedAccountIds
            hydrated = true
            Result.success(Unit)
        }.getOrElse { Result.failure(it) }
    }

    override suspend fun block(accountId: String): Result<Unit> = withContext(ioDispatcher) {
        safeCall { api.blockPeer(accountId) }.map {
            cache.value = (cache.value + accountId).distinct()
            Result.success(Unit)
        }.getOrElse { Result.failure(it) }
    }

    override suspend fun unblock(accountId: String): Result<Unit> = withContext(ioDispatcher) {
        safeCall { api.unblockPeer(accountId) }.map {
            cache.value = cache.value.filterNot { it == accountId }
            Result.success(Unit)
        }.getOrElse { Result.failure(it) }
    }

    override suspend fun isBlocked(accountId: String): Boolean {
        if (!hydrated) refresh()
        return accountId in cache.value
    }
}
