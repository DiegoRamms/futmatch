package com.devapplab.config

import com.devapplab.features.auth.authRouting
import com.devapplab.features.user.userRouting
import com.devapplab.model.ErrorResponse
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.createError
import com.devapplab.utils.respond
import com.devapplab.utils.retrieveLocale
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*


fun Application.configureRouting() {

    val environment = environment.config.propertyOrNull("ktor.environment")?.getString() ?: "development"

    install(StatusPages) {

        status(HttpStatusCode.TooManyRequests) { call, status ->
            val retryAfter = call.response.headers["Retry-After"]
            call.respondText(text = "429: Too many requests. Wait for $retryAfter seconds.", status = status)
        }

        exception<Throwable> { call, cause ->
            val locale = call.retrieveLocale()
            if (environment == "Development") {
                call.respond(
                    message = ErrorResponse("Error", cause.message ?: "Error App"),
                    status = HttpStatusCode.BadRequest
                )
                call.respond<ErrorResponse>(locale.createError())
            }else  call.respond<ErrorResponse>(locale.createError())
        }

        exception<RequestValidationException> { call, cause ->
            val locale = call.retrieveLocale()
            val descriptionKey =
                cause.reasons.firstOrNull()?.let { reason -> StringResourcesKey.getStringResourcesKey(reason) }
            call.respond<ErrorResponse>(locale.createError(descriptionKey = descriptionKey))
        }
    }

    routing {
        //TODO Validate rate limit with Client

        rateLimit(RateLimitName(RateLimitType.PUBLIC.value)) {
            authRouting()
        }
        authenticate("auth-jwt"){
            userRouting()
        }
    }
}
