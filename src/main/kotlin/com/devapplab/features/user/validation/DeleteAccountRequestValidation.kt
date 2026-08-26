package com.devapplab.features.user.validation

import com.devapplab.model.user.request.DeleteAccountRequest
import com.devapplab.utils.StringResourcesKey
import io.ktor.server.plugins.requestvalidation.ValidationResult

fun DeleteAccountRequest.validate(): ValidationResult =
    when {
        !DeleteAccountRequest.isValidConfirmation(confirmation) ->
            ValidationResult.Invalid(StringResourcesKey.ACCOUNT_DELETION_CONFIRMATION_REQUIRED.value)
        else -> ValidationResult.Valid
    }
