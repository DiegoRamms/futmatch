package com.devapplab.config

import com.devapplab.utils.UUIDSerializer
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import java.util.*

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            serializersModule = SerializersModule {
                contextual(UUID::class, UUIDSerializer)
            }
            ignoreUnknownKeys = true
        })
    }
}
