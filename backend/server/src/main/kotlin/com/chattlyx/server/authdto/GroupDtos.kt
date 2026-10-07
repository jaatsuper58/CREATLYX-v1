package com.chattlyx.server.authdto

import kotlinx.serialization.Serializable

/** Phase 4 (GRP-*) request/response bodies for /v1/groups. */

@Serializable
data class CreateGroupBody(
    val name: String,
    val memberAccountIds: List<String> = emptyList(),
)

@Serializable
data class GroupMemberDto(
    val accountId: String,
    val role: String,
    val joinedAt: Long,
)

@Serializable
data class GroupDto(
    val groupId: String,
    val name: String,
    val createdBy: String,
    val createdAt: Long,
    val membershipVersion: Long,
    val members: List<GroupMemberDto>,
)

@Serializable
data class GroupListResponse(val groups: List<GroupDto>)

@Serializable
data class RenameGroupBody(val name: String)

@Serializable
data class AddMembersBody(val accountIds: List<String>)

@Serializable
data class MembershipVersionResponse(val membershipVersion: Long)

@Serializable
data class PresenceDto(
    val accountId: String,
    val online: Boolean,
    val lastSeenMs: Long? = null,
)

@Serializable
data class BlockListResponse(val blockedAccountIds: List<String>)
