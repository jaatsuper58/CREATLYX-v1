package com.chattlyx.domain.auth

/**
 * Phase 1 auth models (AUTH-01..07, AUTH-10). Plain data classes so the
 * domain module stays platform-free.
 */

/** Result of an OTP request: how long the code lives and resend back-off. */
data class OtpRequestResult(
    val expiresInSeconds: Int,
    val resendAfterSeconds: Int,
)

/** A signed-in session (AUTH-07). Tokens stay in secure storage only. */
data class AuthSession(
    val accountId: String,
    val deviceId: Long,
    val accessTokenExpiresInSeconds: Long,
)

/** User-visible profile (AUTH-04). */
data class Profile(
    val accountId: String,
    val displayName: String,
    val username: String?,
    val about: String,
    val avatarBlobId: String?,
)

/** A registered device on the account (AUTH-07). */
data class DeviceInfo(
    val deviceId: Long,
    val name: String,
    val createdAt: Long,
    val lastSeenAt: Long,
    val current: Boolean,
)

/** Remaining public prekeys for this device (AUTH-06). */
data class KeyCounts(
    val oneTimePrekeys: Int,
    val kyberPrekeys: Int,
)

/** Public key bundle fetched to start a session with a peer device. */
data class PeerKeyBundle(
    val accountId: String,
    val deviceId: Long,
    val identityKey: String?,
    val signedPrekeyId: Int?,
    val signedPrekeyRecord: String?,
    val oneTimePrekeyId: Int?,
    val oneTimePrekeyRecord: String?,
    val kyberPrekeyId: Int?,
    val kyberPrekeyRecord: String?,
)
