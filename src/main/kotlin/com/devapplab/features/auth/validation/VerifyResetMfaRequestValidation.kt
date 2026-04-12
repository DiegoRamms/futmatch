package com.devapplab.features.auth.validation

import com.devapplab.model.mfa.VerifyResetMfaRequest
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.isValidEmail
import io.ktor.server.plugins.requestvalidation.*

fun VerifyResetMfaRequest.validate(): ValidationResult {
    return when {
        !isValidEmail(email) -> ValidationResult.Invalid(StringResourcesKey.AUTH_EMAIL_INVALID_ERROR.value)
        code.isBlank() -> ValidationResult.Invalid(StringResourcesKey.MFA_CODE_INVALID.value)
        else -> ValidationResult.Valid
    }
}
