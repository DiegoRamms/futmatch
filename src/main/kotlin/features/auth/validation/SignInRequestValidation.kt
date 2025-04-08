package com.devapplab.features.auth.validation

import com.devapplab.model.auth.request.SignInRequest
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.isValidEmail
import com.devapplab.utils.isValidPassword
import io.ktor.server.plugins.requestvalidation.*

fun SignInRequest.validate(): ValidationResult {
    return when {
        !isValidEmail(email) -> ValidationResult.Invalid(StringResourcesKey.AUTH_EMAIL_INVALID_ERROR.value)
        !isValidPassword(password) -> ValidationResult.Invalid(StringResourcesKey.AUTH_PASSWORD_INVALID_ERROR.value)
        else -> ValidationResult.Valid
    }
}
