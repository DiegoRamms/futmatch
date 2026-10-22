package com.devapplab.config

import io.ktor.server.application.*
import io.ktor.server.plugins.compression.*

fun Application.configureHTTP() {
    install(Compression)
}
