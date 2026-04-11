package com.devapplab.features.user.validation

import com.devapplab.model.user.Gender
import com.devapplab.model.user.request.UpdateGenderRequest
import com.devapplab.utils.StringResourcesKey
import io.ktor.server.plugins.requestvalidation.*

fun UpdateGenderRequest.validate(): ValidationResult {
    val validGenders = Gender.entries.map { it.name }
    return when {
        gender.uppercase() !in validGenders ->
            ValidationResult.Invalid(StringResourcesKey.USER_PROFILE_GENDER_INVALID.value)
        else -> ValidationResult.Valid
    }
}
