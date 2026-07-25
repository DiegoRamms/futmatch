package com.devapplab.config

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.doublereceive.DoubleReceive

const val DESKTOP_SIGNED_BODY_MAX_BYTES = 1_048_576L

/** Allows the App Check route plugin to hash a desktop body before its controller deserializes it. */
fun Application.configureDesktopRequestBodyCaching() {
    install(DoubleReceive) {
        cacheRawRequest = true
        maxSize(DESKTOP_SIGNED_BODY_MAX_BYTES)
    }
}
