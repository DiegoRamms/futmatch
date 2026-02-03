package com.devapplab.features.match.validation

import com.devapplab.model.match.request.UpdateMatchRequest
import com.devapplab.utils.StringResourcesKey
import io.ktor.server.plugins.requestvalidation.*

fun UpdateMatchRequest.validate(): ValidationResult {
    return when {
        dateTime <= 0L ->
            ValidationResult.Invalid(StringResourcesKey.MATCH_DATE_TIME_INVALID_ERROR.value)

        dateTimeEnd <= dateTime ->
            ValidationResult.Invalid(StringResourcesKey.MATCH_DATE_TIME_END_INVALID_ERROR.value)

        maxPlayers <= 0 ->
            ValidationResult.Invalid(StringResourcesKey.MATCH_MAX_PLAYERS_INVALID_ERROR.value)

        minPlayersRequired !in 1..maxPlayers ->
            ValidationResult.Invalid(StringResourcesKey.MATCH_MIN_PLAYERS_INVALID_ERROR.value)

        matchPriceInCents <= 0L ->
            ValidationResult.Invalid(StringResourcesKey.MATCH_PRICE_INVALID_ERROR.value)

        else -> ValidationResult.Valid
    }
}