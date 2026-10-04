package com.chattlyx.server.dto

import kotlinx.serialization.Serializable

/**
 * GET /v1/config — limits, feature flags and minimum supported version
 * (drives the force-update flow, S51). Additive fields only.
 */
@Serializable
data class ServerConfigDto(
    val minSupportedVersion: String,
    val latestVersion: String,
    val limits: LimitsDto,
    val features: Map<String, Boolean> = emptyMap(),
)

@Serializable
data class LimitsDto(
    val maxGroupMembers: Int,
    val maxMessageChars: Int,
    val maxAttachmentBytes: Long,
    val maxForwardTargets: Int,
    val deleteForEveryoneWindowHours: Int,
    val editWindowMinutes: Int,
    val maxPinnedChats: Int,
    val maxPinnedMessagesPerChat: Int,
)

/** Section 4 limits, frozen as launch defaults. */
fun defaultServerConfig() = ServerConfigDto(
    minSupportedVersion = "0.1.0",
    latestVersion = "0.1.0",
    limits = LimitsDto(
        maxGroupMembers = 256,
        maxMessageChars = 8_000,
        maxAttachmentBytes = 2L * 1024 * 1024 * 1024,
        maxForwardTargets = 5,
        deleteForEveryoneWindowHours = 48,
        editWindowMinutes = 15,
        maxPinnedChats = 5,
        maxPinnedMessagesPerChat = 3,
    ),
    features = mapOf(
        "disappearing_messages" to false, // P1
        "group_calls" to false, // P1
        "updates" to false, // P2
    ),
)
