package com.chattlyx.domain.groups

import com.chattlyx.core.common.error.ChattlyError
import com.chattlyx.core.common.result.Result
import com.chattlyx.core.common.result.map
import javax.inject.Inject

/** GRP-01: validates + creates a group; returns the new group id. */
class CreateGroupUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
) {

    suspend operator fun invoke(name: String, memberAccountIds: List<String>): Result<String> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(
                ChattlyError.Validation(field = "name", messageKey = "validation_group_name_empty"),
            )
        }
        if (trimmed.length > MAX_NAME_LENGTH) {
            return Result.failure(
                ChattlyError.Validation(field = "name", messageKey = "validation_group_name_too_long"),
            )
        }
        return groupRepository.createGroup(trimmed, memberAccountIds.distinct()).map { it.id }
    }

    companion object {
        /** Server rule (GroupService.MAX_NAME_LENGTH). */
        const val MAX_NAME_LENGTH = 64
    }
}

/** GRP-03: cached group list stream. */
class ObserveGroupsUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
) {
    operator fun invoke(): kotlinx.coroutines.flow.Flow<List<Group>> =
        groupRepository.observeGroups()
}

/** GRP-03: one group stream. */
class ObserveGroupUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
) {
    operator fun invoke(groupId: String): kotlinx.coroutines.flow.Flow<Group?> =
        groupRepository.observeGroup(groupId)
}

/** GRP-03: pulls the server group list into the local cache. */
class RefreshGroupsUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
) {
    suspend operator fun invoke(): Result<Unit> = groupRepository.refresh()
}

/** GRP-04: add members (owner/admin enforced server-side). */
class AddGroupMembersUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
) {
    suspend operator fun invoke(groupId: String, accountIds: List<String>): Result<Group> =
        groupRepository.addMembers(groupId, accountIds.distinct())
}

/** GRP-06: leave the group (self-removal). */
class LeaveGroupUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
    private val tokenStore: SelfAccountIdProvider,
) {
    suspend operator fun invoke(groupId: String): Result<Unit> {
        val self = tokenStore.accountId() ?: return Result.failure(ChattlyError.Auth)
        return groupRepository.removeMember(groupId, self)
    }
}

/** Abstraction so the domain layer can resolve the caller without Hilt cycles. */
fun interface SelfAccountIdProvider {
    suspend fun accountId(): String?
}

/** GRP-05: rename (owner/admin enforced server-side). */
class RenameGroupUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
) {
    suspend operator fun invoke(groupId: String, name: String): Result<Group> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(
                ChattlyError.Validation(field = "name", messageKey = "validation_group_name_empty"),
            )
        }
        return groupRepository.rename(groupId, trimmed)
    }
}
