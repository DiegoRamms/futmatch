package com.devapplab.features.auth.validation

import com.devapplab.model.auth.request.AppleRegistrationRequest
import com.devapplab.model.user.USER_LAST_NAME_MAX_LENGTH
import com.devapplab.model.user.USER_NAME_MAX_LENGTH
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.isValidBirthDate
import com.devapplab.utils.isValidCellPhoneNumber
import com.devapplab.utils.validateNameOrLastName
import io.ktor.server.plugins.requestvalidation.ValidationResult

fun AppleRegistrationRequest.validate(): ValidationResult = when {
    identityToken.trim().isBlank() || identityToken.length > 12_000 -> ValidationResult.Invalid(StringResourcesKey.INVALID_JWT_DESCRIPTION.value)
    authorizationCode.trim().isBlank() -> ValidationResult.Invalid(StringResourcesKey.INVALID_JWT_DESCRIPTION.value)
    nonce.trim().isBlank() -> ValidationResult.Invalid(StringResourcesKey.INVALID_JWT_DESCRIPTION.value)
    !name.trim().validateNameOrLastName(USER_NAME_MAX_LENGTH) -> ValidationResult.Invalid(StringResourcesKey.REGISTER_NAME_INVALID_ERROR.value)
    !lastName.trim().validateNameOrLastName(USER_LAST_NAME_MAX_LENGTH) -> ValidationResult.Invalid(StringResourcesKey.REGISTER_LAST_NAME_INVALID_ERROR.value)
    !isValidCellPhoneNumber(phone.trim()) -> ValidationResult.Invalid(StringResourcesKey.REGISTER_PHONE_INVALID_ERROR.value)
    !isValidBirthDate(birthDate) -> ValidationResult.Invalid(StringResourcesKey.REGISTER_BIRTH_DATE_INVALID_ERROR.value)
    country.trim().isBlank() -> ValidationResult.Invalid(StringResourcesKey.LOCATION_COUNTRY_INVALID_ERROR.value)
    else -> ValidationResult.Valid
}
