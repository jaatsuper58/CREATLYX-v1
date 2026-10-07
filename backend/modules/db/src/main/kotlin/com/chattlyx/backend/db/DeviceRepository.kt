package com.chattlyx.backend.db

import java.util.UUID
import javax.sql.DataSource

data class DeviceRow(
    val id: Long,
    val accountId: UUID,
    val name: String,
    val createdAt: Long,
    val lastSeenAt: Long,
    val revokedAt: Long?,
)

class DeviceRepository(private val db: DataSource) {

    fun register(accountId: UUID, name: String, now: Long): Long {
        db.connection.use { connection ->
            connection.prepareStatement(
                "INSERT INTO devices (account_id, name, created_at, last_seen_at) VALUES (?, ?, ?, ?)",
                arrayOf("id"),
            ).use { statement ->
                statement.setObject(1, accountId)
                statement.setString(2, name.take(MAX_NAME))
                statement.setLong(3, now)
                statement.setLong(4, now)
                statement.executeUpdate()
                statement.generatedKeys.use { rs ->
                    check(rs.next()) { "device insert returned no key" }
                    return rs.getLong(1)
                }
            }
        }
    }

    fun isActive(accountId: UUID, deviceId: Long): Boolean {
        db.connection.use { connection ->
            connection.prepareStatement("SELECT revoked_at FROM devices WHERE id = ? AND account_id = ?")
                .use { s ->
                    s.setLong(1, deviceId)
                    s.setObject(2, accountId)
                    s.executeQuery().use { rs ->
                        return rs.next() && rs.getObject("revoked_at") == null
                    }
                }
        }
    }

    fun listActive(accountId: UUID): List<DeviceRow> {
        db.connection.use { connection ->
            connection.prepareStatement(
                "SELECT * FROM devices WHERE account_id = ? AND revoked_at IS NULL ORDER BY created_at DESC",
            ).use { s ->
                s.setObject(1, accountId)
                s.executeQuery().use { rs ->
                    val rows = mutableListOf<DeviceRow>()
                    while (rs.next()) {
                        rows += DeviceRow(
                            id = rs.getLong("id"),
                            accountId = rs.getObject("account_id", UUID::class.java),
                            name = rs.getString("name"),
                            createdAt = rs.getLong("created_at"),
                            lastSeenAt = rs.getLong("last_seen_at"),
                            revokedAt = null,
                        )
                    }
                    return rows
                }
            }
        }
    }

    fun revoke(accountId: UUID, deviceId: Long, now: Long): Boolean {
        db.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE devices SET revoked_at = ? WHERE id = ? AND account_id = ? AND revoked_at IS NULL",
            ).use { s ->
                s.setLong(1, now)
                s.setLong(2, deviceId)
                s.setObject(3, accountId)
                return s.executeUpdate() == 1
            }
        }
    }

    /** NOT-01: stores the FCM data-only push token for a device. */
    fun setPushToken(accountId: UUID, deviceId: Long, token: String, now: Long) {
        db.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE devices SET push_token = ?, push_updated_at = ? WHERE id = ? AND account_id = ?",
            ).use { s2 ->
                s2.setString(1, token.take(MAX_PUSH_TOKEN))
                s2.setLong(2, now)
                s2.setLong(3, deviceId)
                s2.setObject(4, accountId)
                s2.executeUpdate()
            }
        }
    }

    fun pushToken(accountId: UUID, deviceId: Long): String? {
        db.connection.use { connection ->
            connection.prepareStatement(
                "SELECT push_token FROM devices WHERE id = ? AND account_id = ? AND revoked_at IS NULL",
            ).use { s2 ->
                s2.setLong(1, deviceId)
                s2.setObject(2, accountId)
                s2.executeQuery().use { rs ->
                    return if (rs.next()) rs.getString(1) else null
                }
            }
        }
    }

    fun pushTokensForAccount(accountId: UUID): List<Pair<Long, String>> {
        db.connection.use { connection ->
            connection.prepareStatement(
                "SELECT id, push_token FROM devices WHERE account_id = ? AND revoked_at IS NULL AND push_token IS NOT NULL",
            ).use { s2 ->
                s2.setObject(1, accountId)
                s2.executeQuery().use { rs ->
                    val out = mutableListOf<Pair<Long, String>>()
                    while (rs.next()) out += rs.getLong(1) to rs.getString(2)
                    return out
                }
            }
        }
    }

    fun touch(accountId: UUID, deviceId: Long, now: Long) {
        db.connection.use { connection ->
            connection.prepareStatement("UPDATE devices SET last_seen_at = ? WHERE id = ? AND account_id = ?")
                .use { s ->
                    s.setLong(1, now)
                    s.setLong(2, deviceId)
                    s.setObject(3, accountId)
                    s.executeUpdate()
                }
        }
    }

    private companion object {
        const val MAX_NAME = 64
        const val MAX_PUSH_TOKEN = 512
    }
}
