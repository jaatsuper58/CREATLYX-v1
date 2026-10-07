package com.chattlyx.backend.auth

import com.chattlyx.backend.common.ChattlyxServerException
import com.chattlyx.backend.common.Clock
import com.chattlyx.backend.db.AuditRepository
import com.chattlyx.backend.db.DeviceRepository
import com.chattlyx.backend.db.RefreshTokenRepository
import java.util.UUID

/**
 * Token issuance and rotation (AUTH-07): 15-minute JWT access tokens,
 * 30-day rotating refresh tokens stored hashed and device-bound. Reuse of an
 * already-rotated refresh token revokes the whole device (theft signal).
 */
class TokenService(
    private val jwt: JwtService,
    private val refreshTokens: RefreshTokenRepository,
    private val devices: DeviceRepository,
    private val audit: AuditRepository,
    private val clock: Clock,
) {

    data class TokenPair(
        val accessToken: String,
        val refreshToken: String,
        val accessTokenExpiresInSeconds: Long,
        val accountId: UUID,
        val deviceId: Long,
    )

    fun issue(accountId: UUID, deviceId: Long): TokenPair {
        val now = clock.nowMillis()
        val refreshToken = Hashing.randomToken()
        refreshTokens.store(
            tokenHash = Hashing.sha256Hex(refreshToken.encodeToByteArray()),
            accountId = accountId,
            deviceId = deviceId,
            createdAt = now,
            expiresAt = now + REFRESH_TTL_MS,
        )
        return TokenPair(
            accessToken = jwt.sign(accountId, deviceId, ACCESS_TTL_MS),
            refreshToken = refreshToken,
            accessTokenExpiresInSeconds = ACCESS_TTL_MS / 1000,
            accountId = accountId,
            deviceId = deviceId,
        )
    }

    fun refresh(refreshToken: String): TokenPair {
        val now = clock.nowMillis()
        val hash = Hashing.sha256Hex(refreshToken.encodeToByteArray())
        val row = refreshTokens.find(hash)
            ?: throw ChattlyxServerException.Unauthorized("Unknown refresh token", "auth/refresh-unknown")

        if (row.revokedAt != null) {
            throw ChattlyxServerException.Unauthorized("Refresh token revoked", "auth/refresh-revoked")
        }
        if (row.replacedBy != null) {
            // Reuse of a rotated token: assume theft, kill the device session.
            devices.revoke(row.accountId, row.deviceId, now)
            refreshTokens.revokeAllForDevice(row.accountId, row.deviceId, now)
            audit.record(row.accountId, "token.reuse_detected", "device=${row.deviceId}")
            throw ChattlyxServerException.Unauthorized("Refresh token reused", "auth/refresh-reuse")
        }
        if (now > row.expiresAt) {
            throw ChattlyxServerException.Unauthorized("Refresh token expired", "auth/refresh-expired")
        }
        if (!devices.isActive(row.accountId, row.deviceId)) {
            throw ChattlyxServerException.Unauthorized("Device revoked", "auth/device-revoked")
        }

        val newToken = Hashing.randomToken()
        val newHash = Hashing.sha256Hex(newToken.encodeToByteArray())
        refreshTokens.markReplaced(hash, newHash)
        refreshTokens.store(
            tokenHash = newHash,
            accountId = row.accountId,
            deviceId = row.deviceId,
            createdAt = now,
            expiresAt = now + REFRESH_TTL_MS,
        )
        devices.touch(row.accountId, row.deviceId, now)

        return TokenPair(
            accessToken = jwt.sign(row.accountId, row.deviceId, ACCESS_TTL_MS),
            refreshToken = newToken,
            accessTokenExpiresInSeconds = ACCESS_TTL_MS / 1000,
            accountId = row.accountId,
            deviceId = row.deviceId,
        )
    }

    /** Bearer-token verification entry for authenticated routes. */
    fun verifyAccess(accessToken: String): JwtService.Claims {
        val claims = jwt.verify(accessToken)
            ?: throw ChattlyxServerException.Unauthorized("Invalid access token", "auth/token-invalid")

        val accountId = try {
            UUID.fromString(claims.sub)
        } catch (e: IllegalArgumentException) {
            throw ChattlyxServerException.Unauthorized("Malformed subject", "auth/token-invalid")
        }

        if (!devices.isActive(accountId, claims.dev)) {
            throw ChattlyxServerException.Unauthorized("Device revoked", "auth/device-revoked")
        }
        return claims
    }

    fun revokeDevice(accountId: UUID, deviceId: Long) {
        val now = clock.nowMillis()
        devices.revoke(accountId, deviceId, now)
        refreshTokens.revokeAllForDevice(accountId, deviceId, now)
        audit.record(accountId, "device.revoked", "device=$deviceId")
    }

    fun revokeAll(accountId: UUID) {
        refreshTokens.revokeAllForAccount(accountId, clock.nowMillis())
    }

    companion object {
        const val ACCESS_TTL_MS = 15 * 60 * 1000L
        const val REFRESH_TTL_MS = 30L * 24 * 60 * 60 * 1000
    }
}
