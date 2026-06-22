package com.devapplab.features.match.validation

import com.devapplab.model.match.request.RebalanceMatchTeamsRequest
import com.devapplab.utils.StringResourcesKey
import io.ktor.server.plugins.requestvalidation.ValidationResult

fun RebalanceMatchTeamsRequest.validate(): ValidationResult {
    return when {
        players.isEmpty() ->
            ValidationResult.Invalid(StringResourcesKey.MATCH_REBALANCE_PLAYERS_EMPTY_ERROR.value)

        players.map { it.userId }.distinct().size != players.size ->
            ValidationResult.Invalid(StringResourcesKey.MATCH_REBALANCE_DUPLICATE_PLAYERS_ERROR.value)

        else -> ValidationResult.Valid
    }
}
