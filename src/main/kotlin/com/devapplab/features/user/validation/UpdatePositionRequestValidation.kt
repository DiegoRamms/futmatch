package com.devapplab.features.user.validation

import com.devapplab.model.user.PlayerPosition
import com.devapplab.model.user.request.UpdatePositionRequest
import com.devapplab.utils.StringResourcesKey
import io.ktor.server.plugins.requestvalidation.*

fun UpdatePositionRequest.validate(): ValidationResult {
    val validPositions = PlayerPosition.entries.map { it.name }
    return when {
        position.uppercase() !in validPositions ->
            ValidationResult.Invalid(StringResourcesKey.USER_PROFILE_POSITION_INVALID.value)
        else -> ValidationResult.Valid
    }
}
