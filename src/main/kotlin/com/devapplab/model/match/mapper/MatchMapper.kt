package com.devapplab.model.match.mapper

import com.devapplab.model.match.Match
import com.devapplab.model.match.MatchBaseInfo
import com.devapplab.model.match.MatchWithFieldBaseInfo
import com.devapplab.model.match.request.CreateMatchRequest
import com.devapplab.model.match.request.UpdateMatchRequest
import com.devapplab.model.match.response.MatchResponse
import com.devapplab.model.match.response.MatchWithFieldResponse
import java.math.BigDecimal
import java.util.*

fun CreateMatchRequest.toMatch(adminId: UUID): Match {
    return Match(
        fieldId = this.fieldId,
        adminId = adminId,
        dateTime = this.dateTime,
        dateTimeEnd = this.dateTimeEnd,
        maxPlayers = this.maxPlayers,
        minPlayersRequired = this.minPlayersRequired,
        matchPrice = matchPriceInCents.toBigDecimal().movePointLeft(2),
        discountIds = this.discountIds,
        status = this.status,
        genderType = this.genderType,
        playerLevel = this.playerLevel
    )
}

fun UpdateMatchRequest.toMatch(adminId: UUID, matchId: UUID): Match {
    return Match(
        id = matchId,
        fieldId = this.fieldId,
        adminId = adminId,
        dateTime = this.dateTime,
        dateTimeEnd = this.dateTimeEnd,
        maxPlayers = this.maxPlayers,
        minPlayersRequired = this.minPlayersRequired,
        matchPrice = matchPriceInCents.toBigDecimal().movePointLeft(2),
        discountIds = this.discountIds,
        status = this.status,
        genderType = this.genderType,
        playerLevel = this.playerLevel
    )
}

fun MatchBaseInfo.toResponse(): MatchResponse {
    return MatchResponse(
        id = this.id,
        fieldId = this.fieldId,
        dateTime = this.dateTime,
        dateTimeEnd = this.dateTimeEnd,
        maxPlayers = this.maxPlayers,
        minPlayersRequired = this.minPlayersRequired,
        matchPriceInCents = matchPrice.multiply(BigDecimal(100)).longValueExact(),
        discountPriceInCents = 0L, // Set to 0L as base info doesn't carry this
        status = this.status,
        genderType = this.genderType,
        playerLevel = this.playerLevel
    )
}

fun MatchWithFieldBaseInfo.toResponse(): MatchWithFieldResponse {
    return MatchWithFieldResponse(
        matchId = matchId,
        fieldId = fieldId,
        fieldName = fieldName,
        fieldLocation = fieldLocation,
        matchDateTime = matchDateTime,
        matchDateTimeEnd = matchDateTimeEnd,
        matchPriceInCents = matchPrice.multiply(BigDecimal(100)).longValueExact(),
        discountInCents = 0L, // Set to 0L as base info doesn't carry this
        maxPlayers = maxPlayers,
        minPlayersRequired = minPlayersRequired,
        status = status,
        footwearType = footwearType,
        fieldType = fieldType,
        hasParking = hasParking,
        mainImage = mainImage,
        genderType = genderType,
        playerLevel = playerLevel
    )
}