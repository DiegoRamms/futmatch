package com.devapplab.config

import com.devapplab.features.auth.validate
import io.ktor.server.application.*
import io.ktor.server.plugins.requestvalidation.*
import com.devapplab.model.auth.request.RegisterUserRequest

fun Application.configureRequestValidation() {
    install(RequestValidation) {
        validate<RegisterUserRequest> { request ->
            request.validate()
        }
    }
}