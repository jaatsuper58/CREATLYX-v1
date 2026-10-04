package com.chattlyx.backend.db

import java.sql.Connection
import javax.sql.DataSource
import org.slf4j.LoggerFactory

/**
 * Minimal forward-only schema migrator: applies `migrations/V{n}__name.sql`
 * resources in numeric order exactly once, tracking versions in
 * `schema_migrations`. Chosen over Flyway in Phase 1 to keep the dependency
 * surface small; swap later if repeatable/undo migrations become necessary.
 */
class SchemaMigrator(
    private val dataSource: DataSource,
    private val migrationResources: List<String> = discoverMigrations(),
) {

    fun migrate() {
        dataSource.connection.use { connection ->
            ensureTrackingTable(connection)
            val applied = appliedVersions(connection)

            migrationResources
                .sortedBy { versionOf(it) }
                .forEach { resource ->
                    val version = versionOf(resource)
                    if (version in applied) return@forEach

                    val sql = loadResource(resource)
                    logger.info("Applying migration V$version ($resource)")
                    connection.autoCommit = false
                    try {
                        connection.createStatement().use { it.execute(sql) }
                        connection.prepareStatement(
                            "INSERT INTO schema_migrations (version, name, applied_at) VALUES (?, ?, ?)",
                        ).use { insert ->
                            insert.setInt(1, version)
                            insert.setString(2, resource)
                            insert.setLong(3, System.currentTimeMillis())
                            insert.executeUpdate()
                        }
                        connection.commit()
                    } catch (e: Exception) {
                        connection.rollback()
                        throw IllegalStateException("Migration $resource failed", e)
                    } finally {
                        connection.autoCommit = true
                    }
                }
        }
    }

    private fun ensureTrackingTable(connection: Connection) {
        connection.createStatement().use {
            it.execute(
                """
                CREATE TABLE IF NOT EXISTS schema_migrations (
                    version INT PRIMARY KEY,
                    name TEXT NOT NULL,
                    applied_at BIGINT NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    private fun appliedVersions(connection: Connection): Set<Int> {
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT version FROM schema_migrations").use { rs ->
                val versions = mutableSetOf<Int>()
                while (rs.next()) versions += rs.getInt(1)
                return versions
            }
        }
    }

    private fun loadResource(resource: String): String =
        javaClass.classLoader.getResourceAsStream(resource)?.use { it.readBytes().decodeToString() }
            ?: throw IllegalStateException("Migration resource missing: $resource")

    companion object {
        private val logger = LoggerFactory.getLogger(SchemaMigrator::class.java)
        private val VERSION_PATTERN = Regex("""migrations/V(\d+)__.*\.sql""")

        fun versionOf(resource: String): Int =
            VERSION_PATTERN.find(resource)?.groupValues?.get(1)?.toInt()
                ?: throw IllegalArgumentException("Unrecognised migration name: $resource")

        fun discoverMigrations(): List<String> =
            listOf(
                "migrations/V1__accounts_devices.sql",
                "migrations/V2__otp_sessions.sql",
                "migrations/V3__avatar_blobs.sql",
                "migrations/V4__messaging.sql",
                "migrations/V5__attachments.sql",
            )
    }
}
