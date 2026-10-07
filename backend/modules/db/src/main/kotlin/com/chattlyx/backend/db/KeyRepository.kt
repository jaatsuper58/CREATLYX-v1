package com.chattlyx.backend.db

import java.util.UUID
import javax.sql.DataSource

/**
 * Public key material only — private keys never reach the server (AUTH-06).
 * One-time and Kyber prekeys are consumed on fetch (single use).
 */
class KeyRepository(private val db: DataSource) {

    fun storeIdentityKey(accountId: UUID, deviceId: Long, publicKey: ByteArray, now: Long) {
        db.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO identity_keys (account_id, device_id, public_key, created_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (account_id, device_id) DO UPDATE SET public_key = EXCLUDED.public_key
                """.trimIndent(),
            ).use { s ->
                s.setObject(1, accountId)
                s.setLong(2, deviceId)
                s.setBytes(3, publicKey)
                s.setLong(4, now)
                s.executeUpdate()
            }
        }
    }

    fun identityKey(accountId: UUID, deviceId: Long): ByteArray? =
        singleBytes("SELECT public_key FROM identity_keys WHERE account_id = ? AND device_id = ?", accountId, deviceId)

    fun storeSignedPreKey(accountId: UUID, deviceId: Long, prekeyId: Int, record: ByteArray, now: Long) {
        db.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO signed_prekeys (account_id, device_id, prekey_id, record, created_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (account_id, device_id, prekey_id) DO UPDATE SET record = EXCLUDED.record
                """.trimIndent(),
            ).use { s ->
                s.setObject(1, accountId)
                s.setLong(2, deviceId)
                s.setInt(3, prekeyId)
                s.setBytes(4, record)
                s.setLong(5, now)
                s.executeUpdate()
            }
        }
    }

    fun signedPreKey(accountId: UUID, deviceId: Long): Pair<Int, ByteArray>? {
        db.connection.use { connection ->
            connection.prepareStatement(
                "SELECT prekey_id, record FROM signed_prekeys WHERE account_id = ? AND device_id = ? LIMIT 1",
            ).use { s ->
                s.setObject(1, accountId)
                s.setLong(2, deviceId)
                s.executeQuery().use { rs ->
                    return if (rs.next()) rs.getInt(1) to rs.getBytes(2) else null
                }
            }
        }
    }

    /** Bulk insert; duplicates ignored (idempotent replenishment, AUTH-06). */
    fun storeOneTimePreKeys(accountId: UUID, deviceId: Long, prekeys: Map<Int, ByteArray>) {
        if (prekeys.isEmpty()) return
        db.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO one_time_prekeys (account_id, device_id, prekey_id, record)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (account_id, device_id, prekey_id) DO NOTHING
                """.trimIndent(),
            ).use { s ->
                prekeys.forEach { (prekeyId, record) ->
                    s.setObject(1, accountId)
                    s.setLong(2, deviceId)
                    s.setInt(3, prekeyId)
                    s.setBytes(4, record)
                    s.addBatch()
                }
                s.executeBatch()
            }
        }
    }

    fun oneTimePreKeyCount(accountId: UUID, deviceId: Long): Int =
        count("SELECT count(*) FROM one_time_prekeys WHERE account_id = ? AND device_id = ?", accountId, deviceId)

    /** Atomically consumes one one-time prekey (returns null when exhausted). */
    fun consumeOneTimePreKey(accountId: UUID, deviceId: Long): Pair<Int, ByteArray>? {
        db.connection.use { connection ->
            connection.prepareStatement(
                """
                DELETE FROM one_time_prekeys
                WHERE (account_id, device_id, prekey_id) IN (
                    SELECT account_id, device_id, prekey_id FROM one_time_prekeys
                    WHERE account_id = ? AND device_id = ? LIMIT 1
                )
                RETURNING prekey_id, record
                """.trimIndent(),
            ).use { s ->
                s.setObject(1, accountId)
                s.setLong(2, deviceId)
                s.executeQuery().use { rs ->
                    return if (rs.next()) rs.getInt(1) to rs.getBytes(2) else null
                }
            }
        }
    }

    fun storeKyberPreKeys(accountId: UUID, deviceId: Long, prekeys: Map<Int, ByteArray>) {
        if (prekeys.isEmpty()) return
        db.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO kyber_prekeys (account_id, device_id, prekey_id, record)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (account_id, device_id, prekey_id) DO NOTHING
                """.trimIndent(),
            ).use { s ->
                prekeys.forEach { (prekeyId, record) ->
                    s.setObject(1, accountId)
                    s.setLong(2, deviceId)
                    s.setInt(3, prekeyId)
                    s.setBytes(4, record)
                    s.addBatch()
                }
                s.executeBatch()
            }
        }
    }

    fun kyberPreKeyCount(accountId: UUID, deviceId: Long): Int =
        count(
            "SELECT count(*) FROM kyber_prekeys WHERE account_id = ? AND device_id = ? AND NOT used",
            accountId,
            deviceId,
        )

    fun consumeKyberPreKey(accountId: UUID, deviceId: Long): Pair<Int, ByteArray>? {
        db.connection.use { connection ->
            connection.prepareStatement(
                """
                UPDATE kyber_prekeys SET used = TRUE
                WHERE (account_id, device_id, prekey_id) IN (
                    SELECT account_id, device_id, prekey_id FROM kyber_prekeys
                    WHERE account_id = ? AND device_id = ? AND NOT used LIMIT 1
                )
                RETURNING prekey_id, record
                """.trimIndent(),
            ).use { s ->
                s.setObject(1, accountId)
                s.setLong(2, deviceId)
                s.executeQuery().use { rs ->
                    return if (rs.next()) rs.getInt(1) to rs.getBytes(2) else null
                }
            }
        }
    }

    private fun singleBytes(sql: String, accountId: UUID, deviceId: Long): ByteArray? {
        db.connection.use { connection ->
            connection.prepareStatement(sql).use { s ->
                s.setObject(1, accountId)
                s.setLong(2, deviceId)
                s.executeQuery().use { rs ->
                    return if (rs.next()) rs.getBytes(1) else null
                }
            }
        }
    }

    private fun count(sql: String, accountId: UUID, deviceId: Long): Int {
        db.connection.use { connection ->
            connection.prepareStatement(sql).use { s ->
                s.setObject(1, accountId)
                s.setLong(2, deviceId)
                s.executeQuery().use { rs ->
                    check(rs.next())
                    return rs.getInt(1)
                }
            }
        }
    }
}
