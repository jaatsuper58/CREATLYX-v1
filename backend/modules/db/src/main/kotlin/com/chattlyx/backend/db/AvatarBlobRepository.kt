package com.chattlyx.backend.db

import java.util.UUID
import javax.sql.DataSource

/** Phase 1 avatar blob store (ciphertext only; keys stay in E2EE payloads). */
class AvatarBlobRepository(private val db: DataSource) {

    fun store(id: UUID, ownerId: UUID, ciphertext: ByteArray, now: Long) {
        db.connection.use { connection ->
            connection.prepareStatement(
                "INSERT INTO avatar_blobs (id, owner_id, ciphertext, created_at) VALUES (?, ?, ?, ?)",
            ).use { s ->
                s.setObject(1, id)
                s.setObject(2, ownerId)
                s.setBytes(3, ciphertext)
                s.setLong(4, now)
                s.executeUpdate()
            }
        }
    }

    fun fetch(id: UUID): ByteArray? {
        db.connection.use { connection ->
            connection.prepareStatement("SELECT ciphertext FROM avatar_blobs WHERE id = ?").use { s ->
                s.setObject(1, id)
                s.executeQuery().use { rs ->
                    return if (rs.next()) rs.getBytes(1) else null
                }
            }
        }
    }
}
