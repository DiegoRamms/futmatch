package com.devapplab.config

import com.devapplab.features.auth.authRouting
import com.devapplab.features.field.fieldRouting
import com.devapplab.features.match.matchRouting
import com.devapplab.features.user.userRouting
import com.devapplab.model.AppResult
import com.devapplab.model.ErrorCode
import com.devapplab.model.ErrorResponse
import com.devapplab.utils.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File


fun Application.configureRouting() {

    val environment = environment.config.propertyOrNull("ktor.environment")?.getString() ?: "development"

    install(StatusPages) {

        status(HttpStatusCode.TooManyRequests) { call, status ->
            val retryAfter = call.response.headers["Retry-After"] ?: "60"
            val locale = call.retrieveLocale()
            val failure: AppResult.Failure = locale.createError(
                titleKey = StringResourcesKey.TOO_MANY_REQUESTS_TITLE,
                descriptionKey = StringResourcesKey.TOO_MANY_REQUESTS_DESCRIPTION,
                status = status,
                errorCode = ErrorCode.TOO_MANY_REQUESTS,
                retryAfter
            )
            call.respond<ErrorResponse>(failure)
        }

        exception<RequestValidationException> { call, cause ->
            val locale = call.retrieveLocale()
            val descriptionKey =
                cause.reasons.firstOrNull()?.let { reason -> StringResourcesKey.getStringResourcesKey(reason) }
            call.respond<ErrorResponse>(locale.createError(descriptionKey = descriptionKey))
        }

        exception<ValueAlreadyExistsException> { call, cause ->
            val locale = call.retrieveLocale()
            call.respond<ErrorResponse>(
                locale.createAlreadyExistsError(value = cause.value)
            )
        }

        exception<NotFoundException> { call, _ ->
            val locale = call.retrieveLocale()
            call.respond<ErrorResponse>(locale.createNotFoundError())
        }

        exception<AccessDeniedException> { call, cause ->
            this@configureRouting.log.error("[AccessDenied]${call.request.path()}", cause)
            val locale = call.retrieveLocale()
            call.respond<ErrorResponse>(
                locale.createError(
                    titleKey = StringResourcesKey.ACCESS_DENIED_TITLE,
                    descriptionKey = StringResourcesKey.ACCESS_DENIED_DESCRIPTION,
                    status = HttpStatusCode.Forbidden,
                    errorCode = ErrorCode.ACCESS_DENIED
                )
            )
        }


        exception<Throwable> { call, cause ->
            val locale = call.retrieveLocale()
            this@configureRouting.log.error("Unhandled exception on ${call.request.path()}", cause)
            val error = if (environment == "Development") {
                AppResult.Failure(
                    ErrorResponse(
                        title = "Unexpected error",
                        message = cause.message ?: "An unexpected error occurred",
                        errorCode = ErrorCode.GENERAL_ERROR
                    ),
                    appStatus = HttpStatusCode.InternalServerError
                )
            } else {
                locale.createError(
                    status = HttpStatusCode.InternalServerError,
                    errorCode = ErrorCode.GENERAL_ERROR
                )
            }

            call.respond<ErrorResponse>(error.appStatus, error.errorResponse)
        }


    }

    routing {
        //TODO Validate rate limit with Client
        staticFiles("/uploads", File("uploads"))
        rateLimit(RateLimitName(RateLimitType.PUBLIC.value)) {
            authRouting()
        }
        authenticate("auth-jwt") {
            userRouting()
            fieldRouting()
            matchRouting()
        }
    }
}
