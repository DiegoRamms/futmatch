package com.devapplab.config

import com.devapplab.features.auth.validation.validate
import features.field.validation.validate
import com.devapplab.features.match.validation.validate
import com.devapplab.model.auth.request.*
import com.devapplab.model.auth.response.RefreshJWTRequest
import model.field.request.CreateFieldRequest
import com.devapplab.model.match.request.CreateMatchRequest
import model.user.request.UpdatePasswordRequest
import io.ktor.server.application.*
import io.ktor.server.plugins.requestvalidation.*
import model.field.request.UpdateFieldRequest
import model.mfa.MfaCodeRequest
import model.mfa.MfaCodeVerificationRequest
import model.mfa.VerifyResetMfaRequest

fun Application.configureRequestValidation() {
    install(RequestValidation) {
        validate<RegisterUserRequest> { request ->
            request.validate()
        }
        validate<SignInRequest> { request ->
            request.validate()
        }
        validate<ForgotPasswordRequest> { request ->
            request.validate()
        }
        validate<MfaCodeRequest> { request ->
            request.validate()
        }
        validate<MfaCodeVerificationRequest> { request ->
            request.validate()
        }
        validate<RefreshJWTRequest> { request ->
            request.validate()
        }
        validate<SignOutRequest> { request ->
            request.validate()
        }
        validate<UpdatePasswordRequest> { request ->
            request.validate()
        }
        validate<VerifyResetMfaRequest> { request ->
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