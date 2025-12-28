package com.devapplab.features.auth.validation

import com.devapplab.model.auth.request.ForgotPasswordRequest
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.isValidEmail
import io.ktor.server.plugins.requestvalidation.*

fun ForgotPasswordRequest.validate(): ValidationResult {
    return when {
        !isValidEmail(email) -> ValidationResult.Invalid(StringResourcesKey.REGISTER_EMAIL_INVALID_ERROR.value)
        else -> ValidationResult.Valid
    }
}
