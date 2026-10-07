package com.chattlyx.server

import com.chattlyx.server.plugins.configureErrorHandling
import com.chattlyx.server.plugins.configureLogging
import com.chattlyx.server.plugins.configureSerialization
import com.chattlyx.server.routes.configureRouting
import io.ktor.server.application.Application

/** DB-free wiring for Phase 0 route tests (health, config, error shape). */
fun Application.testModule() {
    configureSerialization()
    configureLogging()
    configureErrorHandling()
    configureRouting()
}
