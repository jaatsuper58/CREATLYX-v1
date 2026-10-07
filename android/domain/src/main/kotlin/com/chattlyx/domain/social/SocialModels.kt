package com.chattlyx.domain.social

import com.chattlyx.core.common.result.Result
import kotlinx.coroutines.flow.Flow

/** Phase 6: presence (STS) + block list (SAF). */

/** Presence snapshot for one account. */
data class PresenceInfo(
    val accountId: String,
    val online: Boolean,
    val lastSeenMs: Long?,
)

/** STS presence reads. */
interface PresenceRepository {
    suspend fun presenceFor(accountId: String): Result<PresenceInfo>
}

/** SAF block management. */
interface BlockRepository {
    fun observeBlocked(): Flow<List<String>>
    suspend fun refresh(): Result<Unit>
    suspend fun block(accountId: String): Result<Unit>
    suspend fun unblock(accountId: String): Result<Unit>
    suspend fun isBlocked(accountId: String): Boolean
}

/** STS: presence for one peer. */
class GetPresenceUseCase @javax.inject.Inject constructor(
    private val presenceRepository: PresenceRepository,
) {
    suspend operator fun invoke(accountId: String): Result<PresenceInfo> =
        presenceRepository.presenceFor(accountId)
}

/** SAF: block a peer. */
class BlockPeerUseCase @javax.inject.Inject constructor(
    private val blockRepository: BlockRepository,
) {
    suspend operator fun invoke(accountId: String): Result<Unit> = blockRepository.block(accountId)
}

/** SAF: unblock a peer. */
class UnblockPeerUseCase @javax.inject.Inject constructor(
    private val blockRepository: BlockRepository,
) {
    suspend operator fun invoke(accountId: String): Result<Unit> = blockRepository.unblock(accountId)
}

/** SAF: stream of blocked account ids. */
class ObserveBlockedUseCase @javax.inject.Inject constructor(
    private val blockRepository: BlockRepository,
) {
    operator fun invoke(): Flow<List<String>> = blockRepository.observeBlocked()
}
