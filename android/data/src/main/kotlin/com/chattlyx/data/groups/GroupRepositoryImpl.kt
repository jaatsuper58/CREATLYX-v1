package com.chattlyx.data.groups

import com.chattlyx.core.common.dispatchers.ChattlyxDispatcher
import com.chattlyx.core.common.dispatchers.Dispatcher
import com.chattlyx.core.common.result.Result
import com.chattlyx.core.common.result.getOrElse
import com.chattlyx.core.common.result.map
import com.chattlyx.core.database.dao.ConversationDao
import com.chattlyx.core.database.dao.GroupDao
import com.chattlyx.core.database.entity.ConversationEntity
import com.chattlyx.core.database.entity.GroupEntity
import com.chattlyx.core.database.entity.GroupMemberEntity
import com.chattlyx.core.network.rest.AddMembersDto
import com.chattlyx.core.network.rest.ChattlyxServiceApi
import com.chattlyx.core.network.rest.CreateGroupDto
import com.chattlyx.core.network.rest.GroupDto
import com.chattlyx.core.network.rest.RenameGroupDto
import com.chattlyx.core.network.rest.safeCall
import com.chattlyx.domain.groups.Group
import com.chattlyx.domain.groups.GroupMember
import com.chattlyx.domain.groups.GroupRepository
import com.chattlyx.domain.groups.GroupRole
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * GRP-* groups: server is the membership source of truth; the local cache
 * exists so lists render instantly and group sends resolve member fan-outs.
 * Conversation rows with id `grp:<groupId>` keep groups in the chat list.
 */
@Singleton
class GroupRepositoryImpl @Inject constructor(
    private val api: ChattlyxServiceApi,
    private val groupDao: GroupDao,
    private val conversationDao: ConversationDao,
    @Dispatcher(ChattlyxDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : GroupRepository {

    override fun observeGroups(): Flow<List<Group>> =
        groupDao.observeGroups().map { rows -> rows.map { it.toDomain(emptyList()) } }

    override fun observeGroup(groupId: String): Flow<Group?> =
        groupDao.observeGroup(groupId).map { row ->
            row?.toDomain(groupDao.membersOf(row.id).map { it.toDomain() })
        }

    override suspend fun membersOf(groupId: String): List<GroupMember> =
        withContext(ioDispatcher) {
            groupDao.membersOf(groupId).map { it.toDomain() }
        }

    override suspend fun createGroup(name: String, memberAccountIds: List<String>): Result<Group> =
        withContext(ioDispatcher) {
            safeCall { api.createGroup(CreateGroupDto(name, memberAccountIds)) }
                .map { dto ->
                    persist(dto)
                    dto.toDomain(emptyList())
                }
        }

    override suspend fun refresh(): Result<Unit> = withContext(ioDispatcher) {
        safeCall { api.listGroups() }.map { response ->
            response.groups.forEach { persist(it) }
            Result.success(Unit)
        }.getOrElse { Result.failure(it) }
    }

    override suspend fun rename(groupId: String, name: String): Result<Group> =
        withContext(ioDispatcher) {
            safeCall { api.renameGroup(groupId, RenameGroupDto(name)) }
                .map { dto ->
                    persist(dto)
                    dto.toDomain(emptyList())
                }
        }

    override suspend fun addMembers(groupId: String, accountIds: List<String>): Result<Group> =
        withContext(ioDispatcher) {
            safeCall { api.addGroupMembers(groupId, AddMembersDto(accountIds)) }
                .map { dto ->
                    persist(dto)
                    dto.toDomain(emptyList())
                }
        }

    override suspend fun removeMember(groupId: String, accountId: String): Result<Unit> =
        withContext(ioDispatcher) {
            safeCall { api.removeGroupMember(groupId, accountId) }.map {
                val remaining = groupDao.membersOf(groupId).filter { it.accountId != accountId }
                groupDao.upsertMembers(remaining)
                Result.success(Unit)
            }.getOrElse { Result.failure(it) }
        }

    /** GRP-* realtime hook: re-fetch one group after a GroupUpdateFrame. */
    suspend fun refreshGroup(groupId: String) = withContext(ioDispatcher) {
        safeCall { api.group(groupId) }.map { dto ->
            persist(dto)
            Result.success(Unit)
        }.getOrElse { Result.failure(it) }
    }

    // ------------------------------------------------------------------

    /** Writes group + members + chat-list row atomically enough for the UI. */
    private suspend fun persist(dto: GroupDto) {
        val conversationId = "grp:${dto.groupId}"
        groupDao.upsertGroup(
            GroupEntity(
                id = dto.groupId,
                name = dto.name,
                createdBy = dto.createdBy,
                membershipVersion = dto.membershipVersion,
                createdAt = dto.createdAt,
            ),
        )
        groupDao.deleteMembers(dto.groupId)
        groupDao.upsertMembers(
            dto.members.map {
                GroupMemberEntity(dto.groupId, it.accountId, it.role, it.joinedAt)
            },
        )
        val existing = conversationDao.byIdOnce(conversationId)
        if (existing == null) {
            conversationDao.upsert(
                ConversationEntity(
                    id = conversationId,
                    peerAccountId = dto.groupId,
                    peerName = dto.name,
                ),
            )
        } else if (existing.peerName != dto.name) {
            conversationDao.renamePeer(conversationId, dto.name)
        }
    }

    private fun GroupEntity.toDomain(members: List<GroupMember>) = Group(
        id = id,
        name = name,
        createdBy = createdBy,
        membershipVersion = membershipVersion,
        createdAt = createdAt,
        members = members,
    )

    private fun GroupMemberEntity.toDomain() = GroupMember(
        accountId = accountId,
        role = when (role) {
            "owner" -> GroupRole.OWNER
            "admin" -> GroupRole.ADMIN
            else -> GroupRole.MEMBER
        },
        joinedAt = joinedAt,
    )

    private fun GroupDto.toDomain(members: List<GroupMember>) = Group(
        id = groupId,
        name = name,
        createdBy = createdBy,
        membershipVersion = membershipVersion,
        createdAt = createdAt,
        members = members.ifEmpty {
            this.members.map {
                GroupMember(
                    accountId = it.accountId,
                    role = when (it.role) {
                        "owner" -> GroupRole.OWNER
                        "admin" -> GroupRole.ADMIN
                        else -> GroupRole.MEMBER
                    },
                    joinedAt = it.joinedAt,
                )
            }
        },
    )
}
