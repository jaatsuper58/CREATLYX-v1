package com.chattlyx.data.social

import com.chattlyx.core.common.result.Result
import com.chattlyx.core.database.dao.BlockedPeerDao
import com.chattlyx.core.database.entity.BlockedPeerEntity
import com.chattlyx.core.network.rest.BlockListDto
import com.chattlyx.core.network.rest.ChattlyxServiceApi
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import retrofit2.Response

/**
 * SAF-01 hardening: the block list cache is Room-backed, so enforcement
 * survives process restarts and offline windows (server stays source of
 * truth; refresh reconciles).
 */
class BlockRepositoryImplTest {

    /** In-memory stand-in for the Room DAO. */
    private class FakeBlockedPeerDao : BlockedPeerDao {
        val rows = MutableStateFlow<List<BlockedPeerEntity>>(emptyList())

        override fun observeAccountIds(): Flow<List<String>> =
            kotlinx.coroutines.flow.flow { emit(rows.value.map { it.accountId }) }

        override suspend fun snapshot(): List<String> = rows.value.map { it.accountId }

        override suspend fun contains(accountId: String): Boolean =
            rows.value.any { it.accountId == accountId }

        override suspend fun insert(entry: BlockedPeerEntity) {
            rows.value = rows.value.filterNot { it.accountId == entry.accountId } + entry
        }

        override suspend fun delete(accountId: String) {
            rows.value = rows.value.filterNot { it.accountId == accountId }
        }

        override suspend fun clear() {
            rows.value = emptyList()
        }

        override suspend fun insertAll(entries: List<BlockedPeerEntity>) {
            entries.forEach { insert(it) }
        }
    }

    private val api = mockk<ChattlyxServiceApi>()
    private val dao = FakeBlockedPeerDao()
    private val repository = BlockRepositoryImpl(api, dao, UnconfinedTestDispatcher())

    @Test
    fun `refresh persists server list into room cache`() = runTest {
        coEvery { api.blockList() } returns
            Response.success(BlockListDto(listOf("peer-1", "peer-2")))

        val result = repository.refresh()

        assertIs<Result.Success<Unit>>(result)
        assertEquals(listOf("peer-1", "peer-2"), dao.snapshot().sorted())
        assertEquals(listOf("peer-1", "peer-2"), repository.observeBlocked().first().sorted())
    }

    @Test
    fun `isBlocked hydrates from persisted cache when server is unreachable`() = runTest {
        dao.insert(BlockedPeerEntity(accountId = "persisted-peer", blockedAt = 1L))
        coEvery { api.blockList() } throws java.io.IOException("offline")

        assertTrue(repository.isBlocked("persisted-peer"))
        assertFalse(repository.isBlocked("someone-else"))
    }

    @Test
    fun `block writes through to dao and cache`() = runTest {
        coEvery { api.blockPeer("peer-9") } returns Response.success(Unit)

        val result = repository.block("peer-9")

        assertIs<Result.Success<Unit>>(result)
        assertTrue(dao.contains("peer-9"))
        assertTrue(repository.isBlocked("peer-9"))
    }

    @Test
    fun `unblock removes from dao and cache`() = runTest {
        dao.insert(BlockedPeerEntity(accountId = "peer-9", blockedAt = 1L))
        coEvery { api.blockList() } returns Response.success(BlockListDto(listOf("peer-9")))
        assertIs<Result.Success<Unit>>(repository.refresh()) // hydrate mirror
        coEvery { api.unblockPeer("peer-9") } returns Response.success(Unit)

        val result = repository.unblock("peer-9")

        assertIs<Result.Success<Unit>>(result)
        assertFalse(dao.contains("peer-9"))
        assertFalse(repository.isBlocked("peer-9"))
    }

    @Test
    fun `failed block does not poison the cache`() = runTest {
        coEvery { api.blockPeer("peer-9") } throws java.io.IOException("boom")

        val result = repository.block("peer-9")

        assertIs<Result.Failure>(result)
        assertFalse(dao.contains("peer-9"))
    }
}
