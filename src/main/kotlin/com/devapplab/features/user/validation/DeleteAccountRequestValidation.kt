package com.devapplab.features.user.validation

import com.devapplab.model.user.request.DeleteAccountRequest
import com.devapplab.utils.StringResourcesKey
import io.ktor.server.plugins.requestvalidation.ValidationResult

fun DeleteAccountRequest.validate(): ValidationResult =
    when {
        confirmation.trim().uppercase() != DeleteAccountRequest.REQUIRED_CONFIRMATION ->
            ValidationResult.Invalid(StringResourcesKey.ACCOUNT_DELETION_CONFIRMATION_REQUIRED.value)
        password.isNullOrBlank() && (provider == null || identityToken.isNullOrBlank()) ->
            ValidationResult.Invalid(StringResourcesKey.ACCOUNT_DELETION_PASSWORD_REQUIRED.value)
        provider != null && nonce.isNullOrBlank() ->
            ValidationResult.Invalid(StringResourcesKey.ACCOUNT_DELETION_PASSWORD_REQUIRED.value)
        else -> ValidationResult.Valid
    }
