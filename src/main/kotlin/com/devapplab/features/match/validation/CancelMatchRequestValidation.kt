package com.devapplab.features.match.validation

import com.devapplab.model.match.request.CancelMatchRequest
import com.devapplab.utils.StringResourcesKey
import io.ktor.server.plugins.requestvalidation.*

const val CANCEL_MATCH_REASON_MAX_LENGTH = 300

fun CancelMatchRequest.validate(): ValidationResult {
    val trimmed = reason.trim()
    return when {
        trimmed.isEmpty() ->
            ValidationResult.Invalid(StringResourcesKey.MATCH_CANCEL_REASON_REQUIRED_ERROR.value)

        trimmed.length > CANCEL_MATCH_REASON_MAX_LENGTH ->
            ValidationResult.Invalid(StringResourcesKey.MATCH_CANCEL_REASON_TOO_LONG_ERROR.value)

        else -> ValidationResult.Valid
    }
}
