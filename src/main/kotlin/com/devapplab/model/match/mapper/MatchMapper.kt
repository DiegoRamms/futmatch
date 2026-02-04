package com.devapplab.model.match.mapper

import com.devapplab.model.discount.DiscountType
import com.devapplab.model.match.*
import com.devapplab.model.match.request.CreateMatchRequest
import com.devapplab.model.match.request.UpdateMatchRequest
import com.devapplab.model.match.response.*
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

fun MatchWithField.toMatchSummaryResponse(distanceKm: Double?): MatchSummaryResponse {
    // Calculate discounts
    var finalPrice = this.matchPrice
    this.discounts.forEach { discount ->
        finalPrice = when (discount.discountType) {
            DiscountType.FIXED_AMOUNT -> finalPrice - discount.value
            DiscountType.PERCENTAGE -> finalPrice * (BigDecimal.ONE - discount.value.divide(BigDecimal(100)))
        }
    }
    if (finalPrice < BigDecimal.ZERO) finalPrice = BigDecimal.ZERO

    val originalPriceInCents = this.matchPrice.multiply(BigDecimal(100)).toLong()
    val finalPriceInCents = finalPrice.multiply(BigDecimal(100)).toLong()
    val totalDiscountInCents = originalPriceInCents - finalPriceInCents

    // Group players by team
    val teamA = this.players.filter { it.team == TeamType.A }
    val teamB = this.players.filter { it.team == TeamType.B }

    val teamASummary = TeamPlayersSummary(
        playerCount = teamA.size,
        players = teamA.map { PlayerSummary(it.userId, it.avatarUrl) }
    )
    val teamBSummary = TeamPlayersSummary(
        playerCount = teamB.size,
        players = teamB.map { PlayerSummary(it.userId, it.avatarUrl) }
    )

    val availableSpots = this.maxPlayers - this.players.size

    return MatchSummaryResponse(
        id = this.matchId,
        fieldName = this.fieldName,
        fieldImageUrl = this.fieldImageUrl,
        startTime = this.dateTime,
        endTime = this.dateTimeEnd,
        originalPriceInCents = originalPriceInCents,
        totalDiscountInCents = totalDiscountInCents,
        priceInCents = finalPriceInCents,
        genderType = this.genderType,
        availableSpots = if (availableSpots < 0) 0 else availableSpots,
        distanceKm = distanceKm,
        teams = TeamSummaryResponse(teamASummary, teamBSummary)
    )
}