package com.devapplab.features.auth.validation

import com.devapplab.model.auth.request.AppleAuthResolveRequest
import com.devapplab.utils.StringResourcesKey
import io.ktor.server.plugins.requestvalidation.ValidationResult

fun AppleAuthResolveRequest.validate(): ValidationResult {
    val token = identityToken.trim()
    return when {
        token.isBlank() || token.length > MAX_ID_TOKEN_LENGTH ->
            ValidationResult.Invalid(StringResourcesKey.INVALID_JWT_DESCRIPTION.value)

        nonce.trim().isBlank() -> ValidationResult.Invalid(StringResourcesKey.INVALID_JWT_DESCRIPTION.value)

        else -> ValidationResult.Valid
    }
}

private const val MAX_ID_TOKEN_LENGTH = 12_000
