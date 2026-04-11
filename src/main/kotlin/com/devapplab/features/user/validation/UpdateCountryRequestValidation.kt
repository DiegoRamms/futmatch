package com.devapplab.features.user.validation

import com.devapplab.model.user.request.UpdateCountryRequest
import com.devapplab.utils.StringResourcesKey
import io.ktor.server.plugins.requestvalidation.*

fun UpdateCountryRequest.validate(): ValidationResult {
    return when {
        countryCode.isBlank() ->
            ValidationResult.Invalid(StringResourcesKey.USER_PROFILE_COUNTRY_INVALID.value)
        else -> ValidationResult.Valid
    }
}
