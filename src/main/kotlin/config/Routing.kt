package com.devapplab.config

import com.devapplab.features.auth.authRouting
import com.devapplab.features.user.userRouting
import com.devapplab.model.ErrorResponse
import com.devapplab.utils.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*


fun Application.configureRouting() {

    val environment = environment.config.propertyOrNull("ktor.environment")?.getString() ?: "development"

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            val locale = call.retrieveLocale()
            if (environment == "development") {
                call.respond(
                    message = ErrorResponse("Error", cause.message ?: "Error App"),
                    status = HttpStatusCode.BadRequest
                )
            } else call.respond(locale.createError())
        }

        exception<RequestValidationException> { call, cause ->
            val locale = call.retrieveLocale()
            val descriptionKey =
                cause.reasons.firstOrNull()?.let { reason -> StringResourcesKey.getStringResourcesKey(reason) }

            call.respond(
                message = locale.createError(descriptionKey = descriptionKey).errorResponse,
                status = HttpStatusCode.BadRequest
            )
        }
    }

    routing {
        authRouting()
        userRouting()
    }
}
