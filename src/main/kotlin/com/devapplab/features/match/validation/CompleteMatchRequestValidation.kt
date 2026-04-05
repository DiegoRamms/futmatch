package com.devapplab.features.match.validation

import com.devapplab.model.match.CompleteMatchRequest
import com.devapplab.utils.StringResourcesKey
import io.ktor.server.plugins.requestvalidation.*

fun CompleteMatchRequest.validate(): ValidationResult {
    return when {
        goals.isEmpty() ->
            ValidationResult.Invalid(StringResourcesKey.MATCH_GOALS_EMPTY_ERROR.value)
            
        goals.count { it.isBestPlayer } > 1 ->
            ValidationResult.Invalid(StringResourcesKey.MATCH_MULTIPLE_BEST_PLAYER_ERROR.value)
            
        goals.any { it.goals < 0 } ->
            ValidationResult.Invalid(StringResourcesKey.MATCH_INVALID_GOALS_ERROR.value)
            
        else -> ValidationResult.Valid
    }
}
