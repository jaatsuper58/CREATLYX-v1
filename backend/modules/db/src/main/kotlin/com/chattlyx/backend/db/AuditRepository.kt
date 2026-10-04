package com.chattlyx.backend.db

import java.util.UUID
import javax.sql.DataSource

/** PII-light audit trail (Section 9.3): actions and ids, never content. */
class AuditRepository(private val db: DataSource) {

    fun record(accountId: UUID?, action: String, detail: String? = null, now: Long = System.currentTimeMillis()) {
        db.connection.use { connection ->
            connection.prepareStatement(
                "INSERT INTO audit_log (account_id, action, detail, created_at) VALUES (?, ?, ?, ?)",
            ).use { s ->
                s.setObject(1, accountId)
                s.setString(2, action)
                s.setString(3, detail)
                s.setLong(4, now)
                s.executeUpdate()
            }
        }
    }
}
