package com.chattlyx.domain.groups

/** Phase 4 (GRP-*): server-known group membership metadata. */

enum class GroupRole { OWNER, ADMIN, MEMBER }

/** One member of a group. */
data class GroupMember(
    val accountId: String,
    val role: GroupRole,
    val joinedAt: Long,
)

/** One group (content is E2EE; this is membership metadata only). */
data class Group(
    val id: String,
    val name: String,
    val createdBy: String,
    val membershipVersion: Long,
    val createdAt: Long,
    val members: List<GroupMember>,
)

/** GRP-* group management surface. */
interface GroupRepository {

    fun observeGroups(): kotlinx.coroutines.flow.Flow<List<Group>>

    fun observeGroup(groupId: String): kotlinx.coroutines.flow.Flow<Group?>

    suspend fun membersOf(groupId: String): List<GroupMember>

    /** GRP-01: creates the group server-side and caches it locally. */
    suspend fun createGroup(name: String, memberAccountIds: List<String>): com.chattlyx.core.common.result.Result<Group>

    /** GRP-03: refreshes the group list from the server. */
    suspend fun refresh(): com.chattlyx.core.common.result.Result<Unit>

    /** GRP-05: renames (owner/admin). */
    suspend fun rename(groupId: String, name: String): com.chattlyx.core.common.result.Result<Group>

    /** GRP-04: adds members (owner/admin). */
    suspend fun addMembers(groupId: String, accountIds: List<String>): com.chattlyx.core.common.result.Result<Group>

    /** GRP-06: self-leave or admin removal. */
    suspend fun removeMember(groupId: String, accountId: String): com.chattlyx.core.common.result.Result<Unit>
}
