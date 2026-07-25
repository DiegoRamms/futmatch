package com.devapplab.features.device.validation

import com.devapplab.model.device.UpdateFcmTokenRequest
import com.devapplab.model.device.ApproveDesktopEnrollmentRequest
import com.devapplab.model.device.CreateDesktopEnrollmentRequest
import com.devapplab.utils.StringResourcesKey
import io.ktor.server.plugins.requestvalidation.*
import java.util.UUID

fun UpdateFcmTokenRequest.validate(): ValidationResult {
    return when {
        deviceId == UUID(0, 0) -> ValidationResult.Invalid(StringResourcesKey.AUTH_DEVICE_ID_INVALID.value)
        fcmToken.isBlank() -> ValidationResult.Invalid(StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY.value)
        deviceInfo.isBlank() -> ValidationResult.Invalid(StringResourcesKey.AUTH_DEVICE_INFO_REQUIRED_DESCRIPTION.value)
        else -> ValidationResult.Valid
    }
}

fun CreateDesktopEnrollmentRequest.validate(): ValidationResult = when {
    label.trim().isEmpty() || label.trim().length > 120 -> ValidationResult.Invalid(StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY.value)
    runCatching { UUID.fromString(deviceId) }.isFailure -> ValidationResult.Invalid(StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY.value)
    publicKey.isBlank() -> ValidationResult.Invalid(StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY.value)
    else -> ValidationResult.Valid
}

fun ApproveDesktopEnrollmentRequest.validate(): ValidationResult = when {
    runCatching { UUID.fromString(enrollmentId) }.isFailure -> ValidationResult.Invalid(StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY.value)
    runCatching { UUID.fromString(nonce) }.isFailure -> ValidationResult.Invalid(StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY.value)
    else -> ValidationResult.Valid
}
