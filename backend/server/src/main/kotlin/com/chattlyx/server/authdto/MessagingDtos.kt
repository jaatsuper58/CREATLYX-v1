package com.chattlyx.server.authdto

import kotlinx.serialization.Serializable

// ---- CON-03 contact discovery ----

@Serializable
data class DiscoveryRequestBody(val hashes: List<String>)

@Serializable
data class DiscoveredContactDto(
    val accountId: String,
    val displayName: String,
    val username: String? = null,
    val avatarBlobId: String? = null,
)

@Serializable
data class DiscoveryResponseDto(val matches: List<DiscoveredContactDto>)

// ---- NOT-01 push token registration ----

@Serializable
data class PushTokenBody(val token: String)

// ---- MSG-06 history sync ----

@Serializable
data class SyncEnvelopeDto(
    /** Base64 proto Envelope (seq and server id set). */
    val envelopeB64: String,
)

@Serializable
data class HistoryResponseDto(
    val conversationId: String,
    val envelopes: List<SyncEnvelopeDto>,
    val complete: Boolean,
)
