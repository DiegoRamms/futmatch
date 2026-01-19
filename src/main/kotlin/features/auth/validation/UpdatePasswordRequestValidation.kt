package com.devapplab.features.auth.validation

import model.user.request.UpdatePasswordRequest
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.isValidPassword
import io.ktor.server.plugins.requestvalidation.*

fun UpdatePasswordRequest.validate(): ValidationResult {
    return when {
        !isValidPassword(newPassword) -> ValidationResult.Invalid(StringResourcesKey.PASSWORD_INVALID.value)
        else -> ValidationResult.Valid
    }
}
