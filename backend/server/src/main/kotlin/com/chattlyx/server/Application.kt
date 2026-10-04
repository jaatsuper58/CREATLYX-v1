package com.chattlyx.server

import com.chattlyx.server.plugins.configureErrorHandling
import com.chattlyx.server.plugins.configureLogging
import com.chattlyx.server.plugins.configureSerialization
import com.chattlyx.server.routes.configureRouting
import io.ktor.server.application.Application

/**
 * ChattlyX API module wiring. Stateless: any instance behind the load
 * balancer can serve any request (master spec Section 3.2).
 */
fun Application.module() {
    configureSerialization()
    configureLogging()
    configureErrorHandling()
    configureRouting()
}
