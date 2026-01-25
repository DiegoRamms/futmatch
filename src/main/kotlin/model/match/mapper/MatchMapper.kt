package com.devapplab.model.match.mapper

import com.devapplab.model.match.request.CreateMatchRequest
import com.devapplab.model.match.response.MatchResponse
import model.match.Match
import model.match.MatchBaseInfo
import model.match.MatchWithFieldBaseInfo
import model.match.response.MatchWithFieldResponse
import java.math.BigDecimal
import java.util.UUID

fun CreateMatchRequest.toMatch(adminId: UUID): Match {
    return Match(
        fieldId = this.fieldId,
        adminId = adminId,
        dateTime = this.dateTime,
        dateTimeEnd = this.dateTimeEnd,
        maxPlayers = this.maxPlayers,
        minPlayersRequired = this.minPlayersRequired,
        matchPrice = matchPriceInCents.toBigDecimal().movePointLeft(2),
        discount = discountInCents.toBigDecimal().movePointLeft(2),
        status = this.status
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
        discountPriceInCents = discountPrice?.multiply(BigDecimal(100))?.longValueExact() ?: 0,
        status = this.status
    )
}

/*
*  it[fieldId] = match.fieldId
            it[adminId] = match.adminId
            it[dateTime] = match.dateTime
            it[dateTimeEnd] = match.dateTimeEnd
            it[maxPlayers] = match.maxPlayers
            it[minPlayersRequired] = match.minPlayersRequired
            it[matchPrice] = match.matchPrice
            it[discount] = match.discount
            it[status] = match.status
* */

fun MatchWithFieldBaseInfo.toResponse(): MatchWithFieldResponse {
    return MatchWithFieldResponse(
        matchId = matchId,
        fieldId = fieldId,
        fieldName = fieldName,
        fieldLocation = fieldLocation,
        matchDateTime = matchDateTime,
        matchDateTimeEnd = matchDateTimeEnd,
        matchPriceInCents = matchPrice.multiply(BigDecimal(100)).longValueExact(),
        discountInCents = discount.multiply(BigDecimal(100)).longValueExact(),
        maxPlayers = maxPlayers,
        minPlayersRequired = minPlayersRequired,
        status = status
    )
}