package com.devapplab

import com.devapplab.config.*
import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureKoin()
    configureAdministration()
    configureSerialization()
    configureDatabase()
    configureHTTP()
    configureSecurity()
    configureRequestValidation()
    configureRouting()
}
