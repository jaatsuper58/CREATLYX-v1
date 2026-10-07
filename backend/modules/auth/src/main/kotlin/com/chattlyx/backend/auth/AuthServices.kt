package com.chattlyx.backend.auth

import com.chattlyx.backend.common.SystemUtcClock
import com.chattlyx.backend.db.AccountRepository
import com.chattlyx.backend.db.AuditRepository
import com.chattlyx.backend.db.AvatarBlobRepository
import com.chattlyx.backend.db.DeviceRepository
import com.chattlyx.backend.db.KeyRepository
import com.chattlyx.backend.db.OtpSessionRepository
import com.chattlyx.backend.db.RefreshTokenRepository
import javax.sql.DataSource

/**
 * Manual DI wiring for the auth bounded context (ADR-0001: no framework).
 * `devMode` gates the magic OTP code and the fake sender — it must never be
 * constructed with devMode=true outside non-prod environments.
 */
class AuthServices private constructor(
    val otpService: OtpService,
    val tokenService: TokenService,
    val jwtService: JwtService,
    val accountRepository: AccountRepository,
    val deviceRepository: DeviceRepository,
    val keyRepository: KeyRepository,
    val avatarBlobRepository: AvatarBlobRepository,
    val auditRepository: AuditRepository,
    val vault: E164Vault,
    val rateLimiter: RateLimiter,
    val devMode: Boolean,
) {

    companion object {

        fun create(config: AuthServiceConfig, dataSource: DataSource): AuthServices {
            val clock = SystemUtcClock
            val accounts = AccountRepository(dataSource)
            val devices = DeviceRepository(dataSource)
            val keys = KeyRepository(dataSource)
            val otpSessions = OtpSessionRepository(dataSource)
            val refreshTokens = RefreshTokenRepository(dataSource)
            val avatars = AvatarBlobRepository(dataSource)
            val audit = AuditRepository(dataSource)

            val vault = E164Vault.fromBase64Key(config.e164KeyBase64)
            val jwt = JwtService(config.jwtSecret.encodeToByteArray()) { clock.nowMillis() }
            val rateLimiter = RateLimiter { clock.nowMillis() }

            // Production wiring point: swap in Twilio Verify / MSG91 / Firebase
            // Phone Auth senders here once credentials are provisioned.
            val sender: OtpSender = FakeOtpSender()

            return AuthServices(
                otpService = OtpService(
                    otpSessions = otpSessions,
                    accounts = accounts,
                    devices = devices,
                    audit = audit,
                    sender = sender,
                    vault = vault,
                    pepper = config.e164Pepper,
                    rateLimiter = rateLimiter,
                    clock = clock,
                    devMode = config.devMode,
                ),
                tokenService = TokenService(
                    jwt = jwt,
                    refreshTokens = refreshTokens,
                    devices = devices,
                    audit = audit,
                    clock = clock,
                ),
                jwtService = jwt,
                accountRepository = accounts,
                deviceRepository = devices,
                keyRepository = keys,
                avatarBlobRepository = avatars,
                auditRepository = audit,
                vault = vault,
                rateLimiter = rateLimiter,
                devMode = config.devMode,
            )
        }
    }
}

data class AuthServiceConfig(
    val jwtSecret: String,
    val e164Pepper: String,
    val e164KeyBase64: String,
    val devMode: Boolean,
) {
    companion object {
        /** Dev defaults only; real deployments must override every value. */
        fun fromEnv(env: Map<String, String> = System.getenv()): AuthServiceConfig = AuthServiceConfig(
            jwtSecret = env["CHATTLYX_JWT_SECRET"] ?: "dev-only-jwt-secret-change-me-0123456789",
            e164Pepper = env["CHATTLYX_E164_PEPPER"] ?: "dev-only-pepper-change-me",
            e164KeyBase64 = env["CHATTLYX_E164_KEY"]
                ?: java.util.Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() }),
            devMode = (env["CHATTLYX_ENV"] ?: "dev") != "prod",
        )
    }
}
