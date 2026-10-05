package com.chattlyx.core.network.rest

import kotlinx.serialization.Serializable

/** Phase 4 (GRP-*) group REST DTOs mirroring /v1/groups (OpenAPI 0.4.0). */

@Serializable
data class CreateGroupDto(
    val name: String,
    val memberAccountIds: List<String> = emptyList(),
)

@Serializable
data class GroupMemberDto(
    val accountId: String,
    val role: String,
    val joinedAt: Long = 0L,
)

@Serializable
data class GroupDto(
    val groupId: String,
    val name: String,
    val createdBy: String,
    val createdAt: Long,
    val membershipVersion: Long,
    val members: List<GroupMemberDto> = emptyList(),
)

@Serializable
data class GroupListResponseDto(val groups: List<GroupDto>)

@Serializable
data class RenameGroupDto(val name: String)

@Serializable
data class AddMembersDto(val accountIds: List<String>)

@Serializable
data class MembershipVersionDto(val membershipVersion: Long)
