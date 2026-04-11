package com.devapplab.features.user.validation

import com.devapplab.model.user.request.UpdateNameRequest
import com.devapplab.utils.StringResourcesKey
import io.ktor.server.plugins.requestvalidation.*

fun UpdateNameRequest.validate(): ValidationResult {
    return when {
        name.isBlank() ->
            ValidationResult.Invalid(StringResourcesKey.USER_PROFILE_NAME_INVALID.value)
        name.length > 100 ->
            ValidationResult.Invalid(StringResourcesKey.USER_PROFILE_NAME_INVALID.value)
        lastName.isBlank() ->
            ValidationResult.Invalid(StringResourcesKey.USER_PROFILE_NAME_INVALID.value)
        lastName.length > 100 ->
            ValidationResult.Invalid(StringResourcesKey.USER_PROFILE_NAME_INVALID.value)
        else -> ValidationResult.Valid
    }
}
