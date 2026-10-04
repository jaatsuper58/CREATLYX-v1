package com.chattlyx.server

import com.chattlyx.backend.auth.AuthServiceConfig
import com.chattlyx.backend.auth.AuthServices
import com.chattlyx.backend.db.DbConfig
import com.chattlyx.backend.db.DbFactory
import com.chattlyx.backend.db.SchemaMigrator
import com.chattlyx.server.plugins.chattlyxBearer
import com.chattlyx.server.plugins.configureErrorHandling
import com.chattlyx.server.plugins.configureLogging
import com.chattlyx.server.plugins.configureSerialization
import com.chattlyx.server.routes.configureRouting
import com.chattlyx.server.routes.installAccountRoutes
import com.chattlyx.server.routes.installAuthRoutes
import com.chattlyx.server.routes.installKeyRoutes
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import javax.sql.DataSource

/**
 * ChattlyX API module wiring. Stateless per node; the shared DataSource is
 * the only local resource. For tests, use [moduleWithContext].
 */
fun Application.module() {
    val dbConfig = DbConfig.fromEnv()
    val dataSource = DbFactory.create(dbConfig)
    SchemaMigrator(dataSource).migrate()

    val authConfig = AuthServiceConfig.fromEnv()
    val authServices = AuthServices.create(authConfig, dataSource)

    moduleWithContext(authServices)
}

/** Test-friendly wiring: injects pre-built services. */
fun Application.moduleWithContext(auth: AuthServices) {
    configureSerialization()
    configureLogging()
    configureErrorHandling()

    install(Authentication) {
        chattlyxBearer(auth.tokenService)
    }

    configureRouting()
    installAuthRoutes(auth)
    installAccountRoutes(auth)
    installKeyRoutes(auth)
}
