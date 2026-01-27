package com.devapplab.config

import io.ktor.server.application.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.calllogging.*
import org.slf4j.event.Level
import java.util.*

fun Application.configureLogger() {
    install(CallLogging) {
        level = Level.INFO
    }
}

fun Application.configureRequestId() {
    install(CallId) {
        header("X-Request-ID")
        generate {
            UUID.randomUUID().toString()
        }
        verify { it.isNotBlank() }
    }
}