package com.chattlyx.core.network.rest

import kotlinx.serialization.Serializable

/**
 * Wire DTOs for the Phase 1 REST surface (backend/openapi contract). Field
 * names match the server exactly; clients never echo phone numbers back.
 */

@Serializable
data class OtpRequestDto(val e164: String, val language: String = "en")

@Serializable
data class OtpRequestResultDto(val expiresInSeconds: Int, val resendAfterSeconds: Int)

@Serializable
data class OtpVerifyDto(val e164: String, val code: String, val deviceName: String)

@Serializable
data class TokenPairDto(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresInSeconds: Long,
    val accountId: String,
    val deviceId: Long,
)

@Serializable
data class RefreshTokenDto(val refreshToken: String)

@Serializable
data class ProfileDto(
    val accountId: String,
    val displayName: String,
    val username: String? = null,
    val about: String = "",
    val avatarBlobId: String? = null,
    val e164Masked: String? = null,
)

@Serializable
data class ProfileUpdateDto(
    val displayName: String,
    val username: String? = null,
    val about: String = "",
    val avatarBlobId: String? = null,
)

@Serializable
data class AvatarUploadResultDto(val blobId: String)

@Serializable
data class DeviceDto(
    val deviceId: Long,
    val name: String,
    val createdAt: Long,
    val lastSeenAt: Long,
    val current: Boolean,
)

@Serializable
data class DeviceListDto(val devices: List<DeviceDto>)

@Serializable
data class SignedPrekeyDto(val prekeyId: Int, val record: String)

@Serializable
data class PrekeyDto(val prekeyId: Int, val record: String)

@Serializable
data class UploadKeysDto(
    val identityKey: String,
    val signedPrekey: SignedPrekeyDto,
    val oneTimePrekeys: List<PrekeyDto> = emptyList(),
    val kyberPrekeys: List<PrekeyDto> = emptyList(),
)

@Serializable
data class KeyCountDto(val oneTimePrekeys: Int, val kyberPrekeys: Int)

@Serializable
data class KeyBundleDto(
    val accountId: String,
    val deviceId: Long,
    val identityKey: String?,
    val signedPrekey: SignedPrekeyDto?,
    val oneTimePrekey: PrekeyDto?,
    val kyberPrekey: PrekeyDto?,
)

@Serializable
data class KeyBundleListDto(val bundles: List<KeyBundleDto>)


// ---- Phase 2: MSG-06 history sync, CON-03 discovery, NOT-01 push ----

@Serializable
data class SyncEnvelopeDto(val envelopeB64: String)

@Serializable
data class HistoryResponseDto(
    val conversationId: String,
    val envelopes: List<SyncEnvelopeDto>,
    val complete: Boolean,
)

@Serializable
data class DiscoveryRequestDto(val hashes: List<String>)

@Serializable
data class DiscoveredContactDto(
    val accountId: String,
    val displayName: String,
    val username: String? = null,
    val avatarBlobId: String? = null,
)

@Serializable
data class DiscoveryResponseDto(val matches: List<DiscoveredContactDto>)

@Serializable
data class PushTokenDto(val token: String)
