package com.devapplab.features.auth.validation

import com.devapplab.model.mfa.MfaCodeVerificationRequest
import com.devapplab.utils.StringResourcesKey
import io.ktor.server.plugins.requestvalidation.*
import java.util.UUID

fun MfaCodeVerificationRequest.validate(): ValidationResult {
    return when {
        code.isBlank() -> ValidationResult.Invalid(StringResourcesKey.MFA_CODE_INVALID.value)
        !challengeToken.isNullOrBlank() -> ValidationResult.Valid
        userId == null || userId == UUID(0, 0) -> ValidationResult.Invalid(StringResourcesKey.AUTH_USER_ID_INVALID.value)
        deviceId == null || deviceId == UUID(0, 0) -> ValidationResult.Invalid(StringResourcesKey.AUTH_DEVICE_ID_INVALID.value)
        else -> ValidationResult.Valid
    }
}
