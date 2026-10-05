package com.chattlyx.server.groups

import com.chattlyx.backend.common.ChattlyxServerException
import com.chattlyx.backend.db.AccountRepository
import com.chattlyx.backend.db.GroupMemberRow
import com.chattlyx.backend.db.GroupRepository
import com.chattlyx.backend.db.GroupRow
import com.chattlyx.proto.Frame
import com.chattlyx.proto.GroupUpdateFrame
import io.ktor.websocket.Frame as WsFrame
import java.util.UUID

/** What routes return: group metadata plus the caller's resolved view. */
data class GroupView(
    val group: GroupRow,
    val members: List<GroupMemberRow>,
)

/**
 * GRP-01..06 group lifecycle: create, read, rename, add/remove members.
 * Group message content is E2EE and never touches this service; membership
 * is the only server-known metadata. Every mutation bumps
 * `membership_version` and pushes a GroupUpdateFrame to live members.
 */
class GroupService(
    private val repository: GroupRepository,
    private val accounts: AccountRepository,
    private val broadcastGroupUpdate: (memberIds: List<UUID>, groupId: UUID, version: Long) -> Unit,
) {

    /** GRP-01: creator becomes owner; every listed member must exist. */
    fun create(creator: UUID, name: String, memberAccountIds: List<UUID>): GroupView {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_NAME_LENGTH) {
            throw ChattlyxServerException.Validation("name must be 1..$MAX_NAME_LENGTH chars")
        }
        val distinct = (memberAccountIds + creator).distinct()
        if (distinct.size > MAX_MEMBERS) {
            throw ChattlyxServerException.Validation("groups hold at most $MAX_MEMBERS members")
        }
        distinct.forEach { accountId ->
            if (accounts.findById(accountId) == null) {
                throw ChattlyxServerException.NotFound("unknown account $accountId")
            }
        }

        val row = GroupRow(
            id = UUID.randomUUID(),
            name = trimmed,
            createdBy = creator,
            createdAt = System.currentTimeMillis(),
            membershipVersion = 1,
        )
        repository.insertGroup(row)
        repository.insertMembers(row.id, distinct.filter { it != creator }, GroupRepository.ROLE_MEMBER, creator)
        repository.insertMembers(row.id, listOf(creator), GroupRepository.ROLE_OWNER, creator)

        val members = repository.membersOf(row.id)
        notifyUpdate(members.map { it.accountId }, row.id, row.membershipVersion)
        return GroupView(row, members)
    }

    /** GRP-03: members only; strangers get 404 (no existence leak). */
    fun get(accountId: UUID, groupId: UUID): GroupView {
        val group = repository.groupById(groupId) ?: throw ChattlyxServerException.NotFound("group")
        requireMember(groupId, accountId)
        return GroupView(group, repository.membersOf(groupId))
    }

    /** GRP-03: the caller's group list. */
    fun list(accountId: UUID): List<GroupView> =
        repository.groupsFor(accountId).map { group ->
            GroupView(group, repository.membersOf(group.id))
        }

    /** GRP-05: admins + owner rename. */
    fun rename(actor: UUID, groupId: UUID, name: String): GroupView {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_NAME_LENGTH) {
            throw ChattlyxServerException.Validation("name must be 1..$MAX_NAME_LENGTH chars")
        }
        requireAdmin(groupId, actor)
        val version = repository.rename(groupId, trimmed)
        val members = repository.membersOf(groupId)
        notifyUpdate(members.map { it.accountId }, groupId, version)
        return GroupView(checkNotNull(repository.groupById(groupId)), members)
    }

    /** GRP-04: admins + owner add members; unknown accounts are rejected. */
    fun addMembers(actor: UUID, groupId: UUID, accountIds: List<UUID>): GroupView {
        requireAdmin(groupId, actor)
        val existing = repository.memberIds(groupId).toSet()
        val fresh = accountIds.distinct().filter { it !in existing }
        if (existing.size + fresh.size > MAX_MEMBERS) {
            throw ChattlyxServerException.Validation("groups hold at most $MAX_MEMBERS members")
        }
        fresh.forEach { accountId ->
            if (accounts.findById(accountId) == null) {
                throw ChattlyxServerException.NotFound("unknown account $accountId")
            }
        }
        val version = repository.addMembers(groupId, fresh, actor)
        val members = repository.membersOf(groupId)
        notifyUpdate(members.map { it.accountId }, groupId, version)
        return GroupView(checkNotNull(repository.groupById(groupId)), members)
    }

    /**
     * GRP-06: self-leave is always allowed; admins can remove anyone but the
     * owner. The owner leaving promotes the earliest member instead.
     */
    fun removeMember(actor: UUID, groupId: UUID, target: UUID): Long {
        requireMember(groupId, target)
        val actorRole = requireMember(groupId, actor)
        val targetRole = checkNotNull(repository.roleOf(groupId, target))
        val selfAction = actor == target
        if (!selfAction && actorRole == GroupRepository.ROLE_MEMBER) {
            throw ChattlyxServerException.Forbidden("admins manage members")
        }
        if (!selfAction && targetRole == GroupRepository.ROLE_OWNER) {
            throw ChattlyxServerException.Forbidden("the owner cannot be removed")
        }

        if (selfAction && targetRole == GroupRepository.ROLE_OWNER) {
            val successor = repository.membersOf(groupId)
                .filter { it.accountId != target }
                .minByOrNull { it.joinedAt }
            if (successor == null) {
                repository.deleteGroup(groupId)
                return 0L
            }
            repository.promote(groupId, successor.accountId, GroupRepository.ROLE_OWNER)
        }

        val version = repository.removeMember(groupId, target)
        val members = repository.membersOf(groupId)
        notifyUpdate(members.map { it.accountId } + target, groupId, version)
        return version
    }

    // ------------------------------------------------------------------

    private fun requireMember(groupId: UUID, accountId: UUID): String {
        if (repository.groupById(groupId) == null) throw ChattlyxServerException.NotFound("group")
        return repository.roleOf(groupId, accountId)
            ?: throw ChattlyxServerException.NotFound("group")
    }

    private fun requireAdmin(groupId: UUID, accountId: UUID) {
        val role = requireMember(groupId, accountId)
        if (role == GroupRepository.ROLE_MEMBER) {
            throw ChattlyxServerException.Forbidden("admins only")
        }
    }

    private fun notifyUpdate(memberIds: List<UUID>, groupId: UUID, version: Long) {
        broadcastGroupUpdate(memberIds.distinct(), groupId, version)
    }

    companion object {
        const val MAX_NAME_LENGTH = 64
        const val MAX_MEMBERS = 100

        /** Builds the wire frame pushed to live member sessions. */
        fun updateFrame(groupId: UUID, version: Long): WsFrame =
            WsFrame.Binary(
                true,
                Frame.newBuilder()
                    .setGroupUpdate(
                        GroupUpdateFrame.newBuilder()
                            .setGroupId(groupId.toString())
                            .setMembershipVersion(version),
                    )
                    .build()
                    .toByteArray(),
            )
    }
}
