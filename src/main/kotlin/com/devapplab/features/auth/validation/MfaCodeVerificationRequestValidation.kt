package com.devapplab.features.auth.validation

import com.devapplab.model.mfa.MfaCodeVerificationRequest
import com.devapplab.utils.StringResourcesKey
import io.ktor.server.plugins.requestvalidation.*
import java.util.*

fun MfaCodeVerificationRequest.validate(): ValidationResult {
    return when {
        userId == UUID(0, 0) -> ValidationResult.Invalid(StringResourcesKey.AUTH_USER_ID_INVALID.value)
        deviceId == UUID(0, 0) -> ValidationResult.Invalid(StringResourcesKey.AUTH_DEVICE_ID_INVALID.value)
        code.isBlank() -> ValidationResult.Invalid(StringResourcesKey.MFA_CODE_INVALID.value)
        else -> ValidationResult.Valid
    }
}
