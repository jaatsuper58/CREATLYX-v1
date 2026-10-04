package com.chattlyx.backend.auth

import java.util.Base64
import java.util.UUID
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * HS256 JWT access tokens (15 min). Hand-rolled on JCA to avoid a token
 * library dependency; format follows RFC 7519. Keys come from the secret
 * manager (env in dev); rotate via key-id if ever needed.
 */
class JwtService(secret: ByteArray, private val clock: () -> Long = System::currentTimeMillis) {

    private val key = SecretKeySpec(secret, "HmacSHA256")

    @Serializable
    data class Claims(
        val sub: String, // accountId
        val dev: Long, // deviceId
        val iat: Long,
        val exp: Long,
        val typ: String = "access",
    )

    fun sign(accountId: UUID, deviceId: Long, ttlMillis: Long): String {
        val now = clock()
        val claims = Claims(
            sub = accountId.toString(),
            dev = deviceId,
            iat = now / 1000,
            exp = (now + ttlMillis) / 1000,
        )
        val payload = Json.encodeToString(Claims.serializer(), claims)
        return encode(payload.encodeToByteArray())
    }

    /** Returns claims or null when signature/expiry invalid. */
    fun verify(token: String): Claims? {
        val parts = token.split('.')
        if (parts.size != 3) return null

        val signingInput = (parts[0] + "." + parts[1]).encodeToByteArray()
        val expectedSig = Hashing.hmacSha256(key.encoded, signingInput)
        val actualSig = try {
            Base64.getUrlDecoder().decode(parts[2])
        } catch (e: IllegalArgumentException) {
            return null
        }
        if (!Hashing.constantTimeEquals(expectedSig, actualSig)) return null

        val payload = try {
            String(Base64.getUrlDecoder().decode(parts[1]))
        } catch (e: IllegalArgumentException) {
            return null
        }

        val claims = try {
            Json.decodeFromString(Claims.serializer(), payload)
        } catch (e: Exception) {
            return null
        }

        if (claims.typ != "access") return null
        if (claims.exp * 1000 < clock()) return null
        return claims
    }

    private fun encode(payload: ByteArray): String {
        val header = """{"alg":"HS256","typ":"JWT"}""".encodeToByteArray()
        val enc = Base64.getUrlEncoder().withoutPadding()
        val signingInput = enc.encodeToString(header) + "." + enc.encodeToString(payload)
        val signature = enc.encodeToString(Hashing.hmacSha256(key.encoded, signingInput.encodeToByteArray()))
        return "$signingInput.$signature"
    }
}
