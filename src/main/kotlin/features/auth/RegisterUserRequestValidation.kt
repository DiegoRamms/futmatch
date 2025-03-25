package com.devapplab.features.auth

import io.ktor.server.plugins.requestvalidation.*
import com.devapplab.model.auth.request.RegisterUserRequest
import com.devapplab.model.user.USER_LAST_NAME_MAX_LENGTH
import com.devapplab.model.user.USER_NAME_MAX_LENGTH
import com.devapplab.utils.*


fun RegisterUserRequest.validate(): ValidationResult {
    return when {
        !name.validateNameOrLastName(USER_NAME_MAX_LENGTH) ->
            ValidationResult.Invalid(StringResourcesKey.REGISTER_NAME_INVALID_ERROR.value)

        !lastName.validateNameOrLastName(USER_LAST_NAME_MAX_LENGTH) ->
            ValidationResult.Invalid(StringResourcesKey.REGISTER_LAST_NAME_INVALID_ERROR.value)

        !isValidEmail(email) ->
            ValidationResult.Invalid(StringResourcesKey.REGISTER_EMAIL_INVALID_ERROR.value)

        !isValidCellPhoneNumber(phone) ->
            ValidationResult.Invalid(StringResourcesKey.REGISTER_PHONE_INVALID_ERROR.value)

        !isValidPassword(password) ->
            ValidationResult.Invalid(StringResourcesKey.REGISTER_PASSWORD_INVALID_ERROR.value)

        !isValidBirthDate(birthDate) ->
            ValidationResult.Invalid(StringResourcesKey.REGISTER_BIRTH_DATE_INVALID_ERROR.value)

        else -> ValidationResult.Valid
    }
}