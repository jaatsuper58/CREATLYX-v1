package com.chattlyx.server.authdto

import kotlinx.serialization.Serializable

// ---- OTP ----

@Serializable
data class OtpRequestBody(val e164: String, val language: String = "en")

@Serializable
data class OtpRequestResultDto(val expiresInSeconds: Int, val resendAfterSeconds: Int)

@Serializable
data class OtpVerifyBody(val e164: String, val code: String, val deviceName: String = "Android")

// ---- Tokens ----

@Serializable
data class TokenPairDto(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresInSeconds: Long,
    val accountId: String,
    val deviceId: Long,
)

@Serializable
data class RefreshBody(val refreshToken: String)

// ---- Profile ----

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
data class ProfileUpdateBody(
    val displayName: String,
    val username: String? = null,
    val about: String = "",
    val avatarBlobId: String? = null,
)

@Serializable
data class AvatarUploadResultDto(val blobId: String)

// ---- Devices ----

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

// ---- Keys ----

@Serializable
data class UploadKeysBody(
    val identityKey: String, // base64 public material
    val signedPrekey: SignedPrekeyDto,
    val oneTimePrekeys: List<PrekeyDto> = emptyList(),
    val kyberPrekeys: List<PrekeyDto> = emptyList(),
)

@Serializable
data class SignedPrekeyDto(val prekeyId: Int, val record: String)

@Serializable
data class PrekeyDto(val prekeyId: Int, val record: String)

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
