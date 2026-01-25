package com.devapplab.features.auth.validation

import com.devapplab.model.auth.response.RefreshJWTRequest
import com.devapplab.utils.StringResourcesKey
import io.ktor.server.plugins.requestvalidation.*
import java.util.UUID

fun RefreshJWTRequest.validate(): ValidationResult {
    return when {
        userId == UUID(0, 0) -> ValidationResult.Invalid(StringResourcesKey.AUTH_USER_ID_INVALID.value)
        deviceId == UUID(0, 0) -> ValidationResult.Invalid(StringResourcesKey.AUTH_DEVICE_ID_INVALID.value)
        else -> ValidationResult.Valid
    }
}
