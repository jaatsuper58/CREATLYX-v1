package com.chattlyx.backend.db

import java.util.UUID
import javax.sql.DataSource

/** SAF-*: per-account block list. */
class BlockRepository(private val db: DataSource) {

    fun block(accountId: UUID, blocked: UUID) {
        db.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO blocked_peers (account_id, blocked_account_id)
                VALUES (?, ?) ON CONFLICT DO NOTHING
                """.trimIndent(),
            ).use { s ->
                s.setObject(1, accountId)
                s.setObject(2, blocked)
                s.executeUpdate()
            }
        }
    }

    fun unblock(accountId: UUID, blocked: UUID) {
        db.connection.use { connection ->
            connection.prepareStatement(
                "DELETE FROM blocked_peers WHERE account_id = ? AND blocked_account_id = ?",
            ).use { s ->
                s.setObject(1, accountId)
                s.setObject(2, blocked)
                s.executeUpdate()
            }
        }
    }

    fun blockedBy(accountId: UUID): List<UUID> = db.connection.use { connection ->
        connection.prepareStatement(
            "SELECT blocked_account_id FROM blocked_peers WHERE account_id = ? ORDER BY created_at DESC",
        ).use { s ->
            s.setObject(1, accountId)
            s.executeQuery().use { rs ->
                val ids = mutableListOf<UUID>()
                while (rs.next()) ids += rs.getObject(1, UUID::class.java)
                ids
            }
        }
    }

    fun isBlocked(accountId: UUID, blocked: UUID): Boolean = db.connection.use { connection ->
        connection.prepareStatement(
            "SELECT 1 FROM blocked_peers WHERE account_id = ? AND blocked_account_id = ?",
        ).use { s ->
            s.setObject(1, accountId)
            s.setObject(2, blocked)
            s.executeQuery().use { rs -> rs.next() }
        }
    }
}
