package com.devapplab.features.field.validation

import com.devapplab.model.user.FIELD_NAME_MAX_LENGTH
import com.devapplab.utils.StringResourcesKey
import features.field.validation.validateName
import io.ktor.server.plugins.requestvalidation.*
import model.field.request.UpdateFieldRequest


fun UpdateFieldRequest.validate(): ValidationResult {
    return when {
        !name.validateName(FIELD_NAME_MAX_LENGTH) ->
            ValidationResult.Invalid(StringResourcesKey.FIELD_NAME_INVALID_ERROR.value)
        price <= 0.0 ->
            ValidationResult.Invalid(StringResourcesKey.FIELD_PRICE_INVALID_ERROR.value)

        capacity <= 0 ->
            ValidationResult.Invalid(StringResourcesKey.FIELD_CAPACITY_INVALID_ERROR.value)

        description.isBlank() ->
            ValidationResult.Invalid(StringResourcesKey.FIELD_DESCRIPTION_INVALID_ERROR.value)

        rules.isBlank() ->
            ValidationResult.Invalid(StringResourcesKey.FIELD_RULES_INVALID_ERROR.value)

        else -> ValidationResult.Valid
    }
}