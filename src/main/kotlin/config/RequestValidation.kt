package com.devapplab.config

import com.devapplab.features.auth.validation.validate
import com.devapplab.features.field.validation.validate
import com.devapplab.features.match.validation.validate
import com.devapplab.model.auth.request.RegisterUserRequest
import com.devapplab.model.auth.request.SignInRequest
import com.devapplab.model.field.request.CreateFieldRequest
import com.devapplab.model.match.request.CreateMatchRequest
import io.ktor.server.application.*
import io.ktor.server.plugins.requestvalidation.*
import model.field.request.UpdateFieldRequest

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

        validate<UpdateFieldRequest>{ request ->
            request.validate()
        }

        validate<CreateMatchRequest> { request ->
            request.validate()
        }
    }
}