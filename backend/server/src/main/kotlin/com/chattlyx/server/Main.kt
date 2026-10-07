package com.chattlyx.server

import io.ktor.server.netty.EngineMain

/** Entry point; port/modules come from application.conf. */
fun main(args: Array<String>) {
    EngineMain.main(args)
}
