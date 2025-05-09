package com.devapplab.config

import com.devapplab.utils.UUIDSerializer
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import java.util.*

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(Json {
            encodeDefaults = true
            explicitNulls = false
            prettyPrint = true
            isLenient = true
            serializersModule = SerializersModule {
                contextual(UUID::class, UUIDSerializer)
            }
            ignoreUnknownKeys = true
        })
    }
}
