package com.chattlyx.server

import com.chattlyx.backend.auth.AuthServiceConfig
import com.chattlyx.backend.auth.AuthServices
import com.chattlyx.backend.db.DbConfig
import com.chattlyx.backend.db.DbFactory
import com.chattlyx.backend.db.SchemaMigrator
import com.chattlyx.backend.redis.RedisConfig
import com.chattlyx.server.attachments.AttachmentContext
import com.chattlyx.server.messaging.MessagingContext
import com.chattlyx.server.plugins.chattlyxBearer
import com.chattlyx.server.plugins.configureErrorHandling
import com.chattlyx.server.plugins.configureLogging
import com.chattlyx.server.plugins.configureSerialization
import com.chattlyx.server.routes.configureRouting
import com.chattlyx.server.routes.installAccountRoutes
import com.chattlyx.server.routes.installAttachmentRoutes
import com.chattlyx.server.routes.installAuthRoutes
import com.chattlyx.server.routes.installKeyRoutes
import com.chattlyx.server.routes.installMessagingRoutes
import com.chattlyx.server.websocket.installWsGateway
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.websocket.WebSockets

/**
 * ChattlyX API module wiring. Stateless per node except the WebSocket
 * registry; shared state lives in Postgres + Redis.
 */
fun Application.module() {
    val dbConfig = DbConfig.fromEnv()
    val dataSource = DbFactory.create(dbConfig)
    SchemaMigrator(dataSource).migrate()

    val authConfig = AuthServiceConfig.fromEnv()
    val authServices = AuthServices.create(authConfig, dataSource)

    val messaging = MessagingContext.create(
        dataSource = dataSource,
        redisConfig = RedisConfig.fromEnv(),
        accountRepository = authServices.accountRepository,
        deviceRepository = authServices.deviceRepository,
    )

    val attachments = AttachmentContext.create(dataSource)

    moduleWithContext(authServices, messaging, attachments)
}

/** Test-friendly wiring: injects pre-built services (messaging/attachments optional). */
fun Application.moduleWithContext(
    auth: AuthServices,
    messaging: MessagingContext? = null,
    attachments: AttachmentContext? = null,
) {
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

    if (messaging != null) {
        install(WebSockets)
        installMessagingRoutes(auth, messaging)
        installWsGateway(auth.tokenService, messaging)
    }

    if (attachments != null) {
        installAttachmentRoutes(attachments)
    }
}
