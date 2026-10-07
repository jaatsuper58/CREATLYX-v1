package com.chattlyx.backend.auth

import com.chattlyx.backend.common.ChattlyxServerException
import com.chattlyx.backend.common.Clock
import com.chattlyx.backend.db.AccountRepository
import com.chattlyx.backend.db.AuditRepository
import com.chattlyx.backend.db.DeviceRepository
import com.chattlyx.backend.db.OtpSessionRepository

/**
 * OTP lifecycle (AUTH-02/03): request with growing resend back-off, verify
 * with attempt limits + lockout, then account upsert + device registration.
 */
class OtpService(
    private val otpSessions: OtpSessionRepository,
    private val accounts: AccountRepository,
    private val devices: DeviceRepository,
    private val audit: AuditRepository,
    private val sender: OtpSender,
    private val vault: E164Vault,
    private val pepper: String,
    private val rateLimiter: RateLimiter,
    private val clock: Clock,
    private val devMode: Boolean,
) {

    data class RequestResult(val expiresInSeconds: Int, val resendAfterSeconds: Int)

    fun request(e164: String, clientIp: String): RequestResult {
        requireValidE164(e164)
        val now = clock.nowMillis()
        val hash = vault.hash(e164, pepper)

        rateLimiter.tryAcquire("otp:req:$hash", maxEvents = 5, windowMillis = 3_600_000)?.let {
            throw ChattlyxServerException.RateLimited(retryAfterMs = it)
        }
        rateLimiter.tryAcquire("otp:req:ip:$clientIp", maxEvents = 20, windowMillis = 3_600_000)?.let {
            throw ChattlyxServerException.RateLimited(retryAfterMs = it)
        }

        // Resend back-off: 60s base doubling within an hour (AUTH-03).
        val existing = otpSessions.find(hash)
        if (existing != null && now - existing.lastSentAt < existing.resendAfter) {
            throw ChattlyxServerException.RateLimited(
                retryAfterMs = existing.resendAfter - (now - existing.lastSentAt),
            )
        }

        val code = if (devMode) DEV_MAGIC_CODE else Hashing.randomDigits(OTP_LENGTH)
        val codeHash = Hashing.sha256Hex(
            Hashing.hmacSha256(pepper.encodeToByteArray(), (hash + code).encodeToByteArray()),
        )
        val resendAfter = nextResendDelay(existing, now)

        otpSessions.create(
            e164Hash = hash,
            codeHash = codeHash,
            now = now,
            expiresAt = now + OTP_TTL_MS,
            resendAfter = resendAfter,
        )
        sender.send(e164, code)
        audit.record(null, "otp.requested")

        return RequestResult(
            expiresInSeconds = (OTP_TTL_MS / 1000).toInt(),
            resendAfterSeconds = (resendAfter / 1000).toInt(),
        )
    }

    data class VerifyResult(
        val accountId: java.util.UUID,
        val deviceId: Long,
        val isNewAccount: Boolean,
    )

    fun verify(e164: String, code: String, deviceName: String): VerifyResult {
        requireValidE164(e164)
        if (!code.all(Char::isDigit) || code.length != OTP_LENGTH) {
            throw ChattlyxServerException.Validation("code must be $OTP_LENGTH digits")
        }

        val now = clock.nowMillis()
        val hash = vault.hash(e164, pepper)

        rateLimiter.tryAcquire("otp:verify:$hash", maxEvents = 10, windowMillis = 900_000)?.let {
            throw ChattlyxServerException.RateLimited(retryAfterMs = it)
        }

        val session = otpSessions.find(hash)
            ?: throw ChattlyxServerException.Unauthorized("No OTP session", "auth/otp-not-requested")

        if (session.consumedAt != null) {
            throw ChattlyxServerException.Unauthorized("OTP already used", "auth/otp-consumed")
        }
        if (now > session.expiresAt) {
            throw ChattlyxServerException.Unauthorized("OTP expired", "auth/otp-expired")
        }
        if (now < session.lockoutUntil) {
            throw ChattlyxServerException.RateLimited(retryAfterMs = session.lockoutUntil - now)
        }

        val candidateHash = Hashing.sha256Hex(
            Hashing.hmacSha256(pepper.encodeToByteArray(), (hash + code).encodeToByteArray()),
        )
        val matches = Hashing.constantTimeEquals(
            candidateHash.encodeToByteArray(),
            session.codeHash.encodeToByteArray(),
        )

        if (!matches) {
            val attempts = otpSessions.incrementAttempts(hash)
            if (attempts >= MAX_ATTEMPTS) {
                otpSessions.lockOut(hash, now + LOCKOUT_MS)
                audit.record(null, "otp.locked")
                throw ChattlyxServerException.RateLimited(retryAfterMs = LOCKOUT_MS)
            }
            throw ChattlyxServerException.Unauthorized("Wrong code", "auth/otp-mismatch")
        }

        otpSessions.consume(hash, now)

        val isNewAccount = accounts.findByE164Hash(hash) == null
        val accountId = accounts.createOrTouch(
            e164Hash = hash,
            e164Encrypted = vault.encrypt(e164),
            e164Sha256 = vault.discoveryHash(e164),
            now = now,
        )
        val deviceId = devices.register(accountId, deviceName, now)
        audit.record(accountId, "auth.verified", "device=$deviceId")

        return VerifyResult(accountId, deviceId, isNewAccount)
    }

    private fun nextResendDelay(existing: com.chattlyx.backend.db.OtpSessionRow?, now: Long): Long {
        if (existing == null) return RESEND_BASE_MS
        val elapsed = now - existing.lastSentAt
        return if (elapsed >= existing.resendAfter) {
            (existing.resendAfter * 2).coerceAtMost(RESEND_MAX_MS)
        } else {
            existing.resendAfter
        }
    }

    private fun requireValidE164(e164: String) {
        val valid = e164.startsWith("+") &&
            e164.length in 6..16 &&
            e164.drop(1).all(Char::isDigit)
        if (!valid) {
            throw ChattlyxServerException.Validation("e164 must be + followed by 5-15 digits")
        }
    }

    companion object {
        const val OTP_LENGTH = 6
        const val OTP_TTL_MS = 5 * 60 * 1000L
        const val RESEND_BASE_MS = 60 * 1000L
        const val RESEND_MAX_MS = 30 * 60 * 1000L
        const val MAX_ATTEMPTS = 5
        const val LOCKOUT_MS = 15 * 60 * 1000L

        /** Dev-only magic code (master spec Phase 1 acceptance). */
        const val DEV_MAGIC_CODE = "111111"
    }
}
