package com.chattlyx.backend.db

import java.util.UUID
import javax.sql.DataSource

data class RefreshTokenRow(
    val tokenHash: String,
    val accountId: UUID,
    val deviceId: Long,
    val createdAt: Long,
    val expiresAt: Long,
    val replacedBy: String?,
    val revokedAt: Long?,
)

/** Refresh tokens stored hashed; rotation chain enables reuse detection. */
class RefreshTokenRepository(private val db: DataSource) {

    fun store(tokenHash: String, accountId: UUID, deviceId: Long, createdAt: Long, expiresAt: Long) {
        db.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO refresh_tokens (token_hash, account_id, device_id, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { s ->
                s.setString(1, tokenHash)
                s.setObject(2, accountId)
                s.setLong(3, deviceId)
                s.setLong(4, createdAt)
                s.setLong(5, expiresAt)
                s.executeUpdate()
            }
        }
    }

    fun find(tokenHash: String): RefreshTokenRow? {
        db.connection.use { connection ->
            connection.prepareStatement("SELECT * FROM refresh_tokens WHERE token_hash = ?").use { s ->
                s.setString(1, tokenHash)
                s.executeQuery().use { rs ->
                    if (!rs.next()) return null
                    return RefreshTokenRow(
                        tokenHash = rs.getString("token_hash"),
                        accountId = rs.getObject("account_id", UUID::class.java),
                        deviceId = rs.getLong("device_id"),
                        createdAt = rs.getLong("created_at"),
                        expiresAt = rs.getLong("expires_at"),
                        replacedBy = rs.getString("replaced_by"),
                        revokedAt = rs.getObject("revoked_at") as Long?,
                    )
                }
            }
        }
    }

    /** Marks this token as rotated; reuse of a rotated token is a theft signal. */
    fun markReplaced(tokenHash: String, replacedByHash: String) {
        db.connection.use { connection ->
            connection.prepareStatement("UPDATE refresh_tokens SET replaced_by = ? WHERE token_hash = ?")
                .use { s ->
                    s.setString(1, replacedByHash)
                    s.setString(2, tokenHash)
                    s.executeUpdate()
                }
        }
    }

    fun revoke(tokenHash: String, now: Long) {
        db.connection.use { connection ->
            connection.prepareStatement("UPDATE refresh_tokens SET revoked_at = ? WHERE token_hash = ?")
                .use { s ->
                    s.setLong(1, now)
                    s.setString(2, tokenHash)
                    s.executeUpdate()
                }
        }
    }

    fun revokeAllForDevice(accountId: UUID, deviceId: Long, now: Long) {
        db.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE refresh_tokens SET revoked_at = ? WHERE account_id = ? AND device_id = ? AND revoked_at IS NULL",
            ).use { s ->
                s.setLong(1, now)
                s.setObject(2, accountId)
                s.setLong(3, deviceId)
                s.executeUpdate()
            }
        }
    }

    fun revokeAllForAccount(accountId: UUID, now: Long) {
        db.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE refresh_tokens SET revoked_at = ? WHERE account_id = ? AND revoked_at IS NULL",
            ).use { s ->
                s.setLong(1, now)
                s.setObject(2, accountId)
                s.executeUpdate()
            }
        }
    }
}
