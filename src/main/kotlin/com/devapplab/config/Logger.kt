package com.devapplab.config

import io.ktor.server.application.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.request.*
import org.slf4j.event.Level
import java.util.*

fun Application.configureLogger() {
    install(CallLogging) {
        level = Level.INFO
        // Avoid loading unmaintained Jansi native code (CVE-2026-8484).
        disableDefaultColors()
        format { call ->
            "${call.response.status() ?: "Unhandled"}: ${call.request.httpMethod.value} - " +
                "${call.request.path()} in ${call.processingTimeMillis()}ms"
        }
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
