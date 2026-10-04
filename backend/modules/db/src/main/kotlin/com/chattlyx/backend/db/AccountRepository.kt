package com.chattlyx.backend.db

import com.chattlyx.backend.common.UuidV7
import java.sql.ResultSet
import java.util.UUID
import javax.sql.DataSource

data class AccountRow(
    val id: UUID,
    val e164Hash: String,
    val e164Encrypted: ByteArray,
    val username: String?,
    val displayName: String,
    val about: String,
    val avatarBlobId: UUID?,
    val createdAt: Long,
    val deletedAt: Long?,
)

/** Account persistence. Lookups by peppered E.164 hash only (Section 9.3). */
class AccountRepository(private val db: DataSource) {

    fun findByE164Hash(hash: String): AccountRow? = queryOne(
        "SELECT * FROM accounts WHERE e164_hash = ?",
        hash,
    )

    fun findById(id: UUID): AccountRow? = queryOne("SELECT * FROM accounts WHERE id = ?", id)

    fun findIdByUsername(username: String): UUID? {
        db.connection.use { connection ->
            connection.prepareStatement("SELECT id FROM accounts WHERE username = ? AND deleted_at IS NULL")
                .use { statement ->
                    statement.setString(1, username)
                    statement.executeQuery().use { rs ->
                        return if (rs.next()) rs.getObject(1, UUID::class.java) else null
                    }
                }
        }
    }

    /** Contact-discovery lookup by unpeppered SHA-256 (CON-03). */
    fun findByE164Sha256(sha256: String): AccountRow? = queryOne(
        "SELECT * FROM accounts WHERE e164_sha256 = ? AND deleted_at IS NULL",
        sha256,
    )

    /** Bulk discovery: returns live accounts matching any of the hashes. */
    fun findDiscoveryMatches(sha256Hashes: Collection<String>): List<AccountRow> {
        if (sha256Hashes.isEmpty()) return emptyList()
        val placeholders = sha256Hashes.joinToString(",") { "?" }
        db.connection.use { connection ->
            connection.prepareStatement(
                "SELECT * FROM accounts WHERE e164_sha256 IN ($placeholders) AND deleted_at IS NULL",
            ).use { statement ->
                sha256Hashes.forEachIndexed { index, hash ->
                    statement.setString(index + 1, hash)
                }
                statement.executeQuery().use { rs ->
                    val rows = mutableListOf<AccountRow>()
                    while (rs.next()) rows += rs.toAccountRow()
                    return rows
                }
            }
        }
    }

    /** Upserts by e164_hash; returns the account id. */
    fun createOrTouch(
        e164Hash: String,
        e164Encrypted: ByteArray,
        e164Sha256: String,
        now: Long,
    ): UUID {
        val existing = findByE164Hash(e164Hash)
        if (existing != null) {
            if (existing.deletedAt != null) restore(existing.id, now)
            return existing.id
        }

        val id = UuidV7.generate(now)
        db.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO accounts (id, e164_hash, e164_encrypted, e164_sha256, username, display_name, about, avatar_blob_id, created_at)
                VALUES (?, ?, ?, ?, NULL, ?, '', NULL, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, id)
                statement.setString(2, e164Hash)
                statement.setBytes(3, e164Encrypted)
                statement.setString(4, e164Sha256)
                statement.setString(5, defaultDisplayName(e164Hash))
                statement.setLong(6, now)
                statement.executeUpdate()
            }
        }
        return id
    }

    fun updateProfile(
        id: UUID,
        displayName: String,
        about: String,
        username: String?,
        avatarBlobId: UUID?,
    ) {
        db.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE accounts SET display_name = ?, about = ?, username = ?, avatar_blob_id = ? WHERE id = ?",
            ).use { statement ->
                statement.setString(1, displayName)
                statement.setString(2, about)
                statement.setString(3, username)
                statement.setObject(4, avatarBlobId)
                statement.setObject(5, id)
                statement.executeUpdate()
            }
        }
    }

    /** AUTH-10: soft delete; purge job removes rows within 30 days. */
    fun markDeleted(id: UUID, now: Long) {
        db.connection.use { connection ->
            connection.prepareStatement("UPDATE accounts SET deleted_at = ? WHERE id = ?").use { s ->
                s.setLong(1, now)
                s.setObject(2, id)
                s.executeUpdate()
            }
        }
    }

    private fun restore(id: UUID, now: Long) {
        db.connection.use { connection ->
            connection.prepareStatement("UPDATE accounts SET deleted_at = NULL, created_at = ? WHERE id = ?")
                .use { s ->
                    s.setLong(1, now)
                    s.setObject(2, id)
                    s.executeUpdate()
                }
        }
    }

    private fun defaultDisplayName(e164Hash: String): String =
        "ChattlyX user " + e164Hash.takeLast(4)

    private fun queryOne(sql: String, vararg params: Any?): AccountRow? {
        db.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                params.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                statement.executeQuery().use { rs ->
                    return if (rs.next()) rs.toAccountRow() else null
                }
            }
        }
    }

    private fun ResultSet.toAccountRow() = AccountRow(
        id = getObject("id", UUID::class.java),
        e164Hash = getString("e164_hash"),
        e164Encrypted = getBytes("e164_encrypted"),
        username = getString("username"),
        displayName = getString("display_name"),
        about = getString("about"),
        avatarBlobId = getObject("avatar_blob_id", UUID::class.java),
        createdAt = getLong("created_at"),
        deletedAt = getObject("deleted_at") as Long?,
    )
}
