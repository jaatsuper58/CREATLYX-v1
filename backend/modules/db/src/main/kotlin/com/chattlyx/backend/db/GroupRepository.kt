package com.chattlyx.backend.db

import java.sql.ResultSet
import java.util.UUID
import javax.sql.DataSource

/** One group row as stored by V6. */
data class GroupRow(
    val id: UUID,
    val name: String,
    val createdBy: UUID,
    val createdAt: Long,
    val membershipVersion: Long,
)

/** One membership row. */
data class GroupMemberRow(
    val groupId: UUID,
    val accountId: UUID,
    val role: String,
    val addedBy: UUID?,
    val joinedAt: Long,
)

/**
 * Phase 4 (GRP-*) group + membership store. Pure JDBC, no business rules —
 * those live in the server's GroupService.
 */
class GroupRepository(private val db: DataSource) {

    /**
     * Inserts the group row; [idempotencyKey] (V8) dedupes retried creates.
     * Returns false when a group with the same creator + key already exists.
     */
    fun insertGroup(row: GroupRow, idempotencyKey: String? = null): Boolean {
        return db.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO groups (id, name, created_by, created_at, membership_version, idempotency_key)
                VALUES (?, ?, ?, to_timestamp(?::double precision / 1000), 1, ?)
                """.trimIndent(),
            ).use { s ->
                s.setObject(1, row.id)
                s.setString(2, row.name)
                s.setObject(3, row.createdBy)
                s.setLong(4, row.createdAt)
                if (idempotencyKey == null) s.setNull(5, java.sql.Types.VARCHAR) else s.setString(5, idempotencyKey)
                try {
                    s.executeUpdate() == 1
                } catch (e: java.sql.SQLException) {
                    if (e.sqlState == "23505") false else throw e
                }
            }
        }
    }

    /** V8 idempotency lookup: the original group for a creator + key. */
    fun groupIdByIdempotencyKey(creator: UUID, idempotencyKey: String): UUID? =
        db.connection.use { connection ->
            connection.prepareStatement(
                "SELECT id FROM groups WHERE created_by = ? AND idempotency_key = ?",
            ).use { s ->
                s.setObject(1, creator)
                s.setString(2, idempotencyKey)
                s.executeQuery().use { rs ->
                    if (rs.next()) rs.getObject(1, UUID::class.java) else null
                }
            }
        }

    fun insertMembers(groupId: UUID, accountIds: List<UUID>, role: String, addedBy: UUID) {
        if (accountIds.isEmpty()) return
        db.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO group_members (group_id, account_id, role, added_by)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (group_id, account_id) DO NOTHING
                """.trimIndent(),
            ).use { s ->
                accountIds.forEach { accountId ->
                    s.setObject(1, groupId)
                    s.setObject(2, accountId)
                    s.setString(3, role)
                    s.setObject(4, addedBy)
                    s.addBatch()
                }
                s.executeBatch()
            }
        }
    }

    fun groupById(id: UUID): GroupRow? = db.connection.use { connection ->
        connection.prepareStatement("SELECT id, name, created_by, created_at, membership_version FROM groups WHERE id = ?")
            .use { s ->
                s.setObject(1, id)
                s.executeQuery().use { rs ->
                    if (rs.next()) rs.toGroupRow() else null
                }
            }
    }

    fun membersOf(groupId: UUID): List<GroupMemberRow> = db.connection.use { connection ->
        connection.prepareStatement(
            "SELECT group_id, account_id, role, added_by, joined_at FROM group_members WHERE group_id = ? ORDER BY joined_at",
        ).use { s ->
            s.setObject(1, groupId)
            s.executeQuery().use { rs ->
                val rows = mutableListOf<GroupMemberRow>()
                while (rs.next()) rows += rs.toMemberRow()
                rows
            }
        }
    }

    fun groupsFor(accountId: UUID): List<GroupRow> = db.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT g.id, g.name, g.created_by, g.created_at, g.membership_version
            FROM groups g
            JOIN group_members m ON m.group_id = g.id
            WHERE m.account_id = ?
            ORDER BY g.created_at DESC
            """.trimIndent(),
        ).use { s ->
            s.setObject(1, accountId)
            s.executeQuery().use { rs ->
                val rows = mutableListOf<GroupRow>()
                while (rs.next()) rows += rs.toGroupRow()
                rows
            }
        }
    }

    fun memberIds(groupId: UUID): List<UUID> = db.connection.use { connection ->
        connection.prepareStatement("SELECT account_id FROM group_members WHERE group_id = ?").use { s ->
            s.setObject(1, groupId)
            s.executeQuery().use { rs ->
                val ids = mutableListOf<UUID>()
                while (rs.next()) ids += rs.getObject(1, UUID::class.java)
                ids
            }
        }
    }

    fun roleOf(groupId: UUID, accountId: UUID): String? = db.connection.use { connection ->
        connection.prepareStatement("SELECT role FROM group_members WHERE group_id = ? AND account_id = ?").use { s ->
            s.setObject(1, groupId)
            s.setObject(2, accountId)
            s.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
    }

    fun rename(groupId: UUID, name: String): Long = db.connection.use { connection ->
        connection.prepareStatement(
            "UPDATE groups SET name = ?, membership_version = membership_version + 1 WHERE id = ? RETURNING membership_version",
        ).use { s ->
            s.setString(1, name)
            s.setObject(2, groupId)
            s.executeQuery().use { rs ->
                check(rs.next()) { "group $groupId disappeared" }
                rs.getLong(1)
            }
        }
    }

    /** Adds members and bumps the version in one statement batch. */
    fun addMembers(groupId: UUID, accountIds: List<UUID>, addedBy: UUID): Long {
        insertMembers(groupId, accountIds, ROLE_MEMBER, addedBy)
        return bumpVersion(groupId)
    }

    fun removeMember(groupId: UUID, accountId: UUID): Long {
        db.connection.use { connection ->
            connection.prepareStatement("DELETE FROM group_members WHERE group_id = ? AND account_id = ?").use { s ->
                s.setObject(1, groupId)
                s.setObject(2, accountId)
                s.executeUpdate()
            }
        }
        return bumpVersion(groupId)
    }

    fun promote(groupId: UUID, accountId: UUID, role: String) {
        db.connection.use { connection ->
            connection.prepareStatement("UPDATE group_members SET role = ? WHERE group_id = ? AND account_id = ?").use { s ->
                s.setString(1, role)
                s.setObject(2, groupId)
                s.setObject(3, accountId)
                s.executeUpdate()
            }
        }
    }

    fun memberCount(groupId: UUID): Int = db.connection.use { connection ->
        connection.prepareStatement("SELECT count(*) FROM group_members WHERE group_id = ?").use { s ->
            s.setObject(1, groupId)
            s.executeQuery().use { rs ->
                check(rs.next())
                rs.getInt(1)
            }
        }
    }

    fun deleteGroup(groupId: UUID) {
        db.connection.use { connection ->
            connection.prepareStatement("DELETE FROM groups WHERE id = ?").use { s ->
                s.setObject(1, groupId)
                s.executeUpdate()
            }
        }
    }

    private fun bumpVersion(groupId: UUID): Long = db.connection.use { connection ->
        connection.prepareStatement(
            "UPDATE groups SET membership_version = membership_version + 1 WHERE id = ? RETURNING membership_version",
        ).use { s ->
            s.setObject(1, groupId)
            s.executeQuery().use { rs ->
                check(rs.next()) { "group $groupId disappeared" }
                rs.getLong(1)
            }
        }
    }

    private fun ResultSet.toGroupRow() = GroupRow(
        id = getObject("id", UUID::class.java),
        name = getString("name"),
        createdBy = getObject("created_by", UUID::class.java),
        createdAt = getTimestamp("created_at").time,
        membershipVersion = getLong("membership_version"),
    )

    private fun ResultSet.toMemberRow() = GroupMemberRow(
        groupId = getObject("group_id", UUID::class.java),
        accountId = getObject("account_id", UUID::class.java),
        role = getString("role"),
        addedBy = getObject("added_by", UUID::class.java),
        joinedAt = getTimestamp("joined_at").time,
    )

    companion object {
        const val ROLE_OWNER = "owner"
        const val ROLE_ADMIN = "admin"
        const val ROLE_MEMBER = "member"
    }
}
