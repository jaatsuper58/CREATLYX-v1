package com.chattlyx.backend.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource

/** Connection-pool construction from environment-driven config. */
object DbFactory {

    fun create(config: DbConfig): HikariDataSource {
        val hikari = HikariConfig().apply {
            jdbcUrl = config.jdbcUrl
            username = config.username
            password = config.password
            maximumPoolSize = config.maxPoolSize
            isAutoCommit = true
            addDataSourceProperty("cachePrepStmts", "true")
        }
        return HikariDataSource(hikari)
    }
}

data class DbConfig(
    val jdbcUrl: String = "jdbc:postgresql://localhost:5432/chattlyx",
    val username: String = "chattlyx",
    val password: String = "chattlyx_dev_only",
    val maxPoolSize: Int = 10,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): DbConfig = DbConfig(
            jdbcUrl = env["CHATTLYX_DB_URL"] ?: "jdbc:postgresql://localhost:5432/chattlyx",
            username = env["CHATTLYX_DB_USER"] ?: "chattlyx",
            password = env["CHATTLYX_DB_PASSWORD"] ?: "chattlyx_dev_only",
            maxPoolSize = env["CHATTLYX_DB_POOL_SIZE"]?.toIntOrNull() ?: 10,
        )
    }
}
