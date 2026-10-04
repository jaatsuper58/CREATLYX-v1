package com.chattlyx.backend.db

import javax.sql.DataSource

data class OtpSessionRow(
    val e164Hash: String,
    val codeHash: String,
    val attempts: Int,
    val createdAt: Long,
    val expiresAt: Long,
    val lockoutUntil: Long,
    val resendAfter: Long,
    val lastSentAt: Long,
    val consumedAt: Long?,
)

class OtpSessionRepository(private val db: DataSource) {

    /** Creates or replaces the session for a number (latest request wins). */
    fun create(
        e164Hash: String,
        codeHash: String,
        now: Long,
        expiresAt: Long,
        resendAfter: Long,
    ) {
        db.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO otp_sessions (e164_hash, code_hash, attempts, created_at, expires_at, lockout_until, resend_after, last_sent_at, consumed_at)
                VALUES (?, ?, 0, ?, ?, 0, ?, ?, NULL)
                ON CONFLICT (e164_hash) DO UPDATE
                SET code_hash = EXCLUDED.code_hash,
                    attempts = 0,
                    created_at = EXCLUDED.created_at,
                    expires_at = EXCLUDED.expires_at,
                    lockout_until = 0,
                    resend_after = EXCLUDED.resend_after,
                    last_sent_at = EXCLUDED.last_sent_at,
                    consumed_at = NULL
                """.trimIndent(),
            ).use { s ->
                s.setString(1, e164Hash)
                s.setString(2, codeHash)
                s.setLong(3, now)
                s.setLong(4, expiresAt)
                s.setLong(5, resendAfter)
                s.setLong(6, now)
                s.executeUpdate()
            }
        }
    }

    fun find(e164Hash: String): OtpSessionRow? {
        db.connection.use { connection ->
            connection.prepareStatement("SELECT * FROM otp_sessions WHERE e164_hash = ?").use { s ->
                s.setString(1, e164Hash)
                s.executeQuery().use { rs ->
                    if (!rs.next()) return null
                    return OtpSessionRow(
                        e164Hash = rs.getString("e164_hash"),
                        codeHash = rs.getString("code_hash"),
                        attempts = rs.getInt("attempts"),
                        createdAt = rs.getLong("created_at"),
                        expiresAt = rs.getLong("expires_at"),
                        lockoutUntil = rs.getLong("lockout_until"),
                        resendAfter = rs.getLong("resend_after"),
                        lastSentAt = rs.getLong("last_sent_at"),
                        consumedAt = rs.getObject("consumed_at") as Long?,
                    )
                }
            }
        }
    }

    fun incrementAttempts(e164Hash: String): Int {
        db.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE otp_sessions SET attempts = attempts + 1 WHERE e164_hash = ? RETURNING attempts",
            ).use { s ->
                s.setString(1, e164Hash)
                s.executeQuery().use { rs ->
                    check(rs.next())
                    return rs.getInt(1)
                }
            }
        }
    }

    fun lockOut(e164Hash: String, until: Long) {
        db.connection.use { connection ->
            connection.prepareStatement("UPDATE otp_sessions SET lockout_until = ? WHERE e164_hash = ?")
                .use { s ->
                    s.setLong(1, until)
                    s.setString(2, e164Hash)
                    s.executeUpdate()
                }
        }
    }

    fun consume(e164Hash: String, now: Long) {
        db.connection.use { connection ->
            connection.prepareStatement("UPDATE otp_sessions SET consumed_at = ? WHERE e164_hash = ?")
                .use { s ->
                    s.setLong(1, now)
                    s.setString(2, e164Hash)
                    s.executeUpdate()
                }
        }
    }
}
