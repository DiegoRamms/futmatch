package com.devapplab.features.auth.validation

import com.devapplab.model.auth.response.RefreshJWTRequest
import io.ktor.server.plugins.requestvalidation.*

fun RefreshJWTRequest.validate(): ValidationResult {
    return ValidationResult.Valid
}
