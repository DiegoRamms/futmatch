package com.devapplab.features.auth.validation

import com.devapplab.model.auth.request.SignOutRequest
import com.devapplab.utils.StringResourcesKey
import io.ktor.server.plugins.requestvalidation.*
import java.util.UUID

fun SignOutRequest.validate(): ValidationResult {
    return when {
        deviceId == UUID(0, 0) -> ValidationResult.Invalid(StringResourcesKey.AUTH_DEVICE_ID_INVALID.value)
        else -> ValidationResult.Valid
    }
}
