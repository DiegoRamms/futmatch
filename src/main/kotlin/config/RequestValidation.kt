package com.devapplab.config

import com.devapplab.features.auth.validation.validate
import com.devapplab.features.field.validation.validate
import io.ktor.server.application.*
import io.ktor.server.plugins.requestvalidation.*
import com.devapplab.model.auth.request.RegisterUserRequest
import com.devapplab.model.auth.request.SignInRequest
import com.devapplab.model.field.request.CreateFieldRequest

fun Application.configureRequestValidation() {
    install(RequestValidation) {
        validate<RegisterUserRequest> { request ->
            request.validate()
        }
        validate<SignInRequest> { request ->
            request.validate()
        }
        validate<CreateFieldRequest> { request ->
            request.validate()
        }
    }
}