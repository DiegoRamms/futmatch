package com.devapplab.model.match.mapper

import com.devapplab.model.discount.DiscountType
import com.devapplab.model.location.Location
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

fun MatchWithField.toMatchSummaryResponse(): MatchSummaryResponse {
    val prices = calculatePrices()
    val teams = buildTeamSummary()
    val location = buildLocation()
    val availableSpots = this.maxPlayers - this.players.size

    return MatchSummaryResponse(
        id = this.matchId,
        fieldName = this.fieldName,
        fieldImageUrl = this.fieldImageUrl,
        startTime = this.dateTime,
        endTime = this.dateTimeEnd,
        originalPriceInCents = prices.originalPriceInCents,
        totalDiscountInCents = prices.totalDiscountInCents,
        priceInCents = prices.finalPriceInCents,
        genderType = this.genderType,
        status = this.status,
        availableSpots = if (availableSpots < 0) 0 else availableSpots,
        teams = teams,
        location = location
    )
}

fun MatchWithField.toMatchDetailResponse(): MatchDetailResponse {
    val prices = calculatePrices()
    val teams = buildTeamSummary()
    val location = buildLocation()
    val availableSpots = this.maxPlayers - this.players.size

    return MatchDetailResponse(
        id = this.matchId,
        fieldName = this.fieldName,
        fieldImageUrl = this.fieldImageUrl,
        startTime = this.dateTime,
        endTime = this.dateTimeEnd,
        originalPriceInCents = prices.originalPriceInCents,
        totalDiscountInCents = prices.totalDiscountInCents,
        priceInCents = prices.finalPriceInCents,
        genderType = this.genderType,
        status = this.status,
        availableSpots = if (availableSpots < 0) 0 else availableSpots,
        teams = teams,
        location = location,
        footwearType = this.fieldFootwearType,
        fieldType = this.fieldType,
        hasParking = this.fieldHasParking,
        extraInfo = this.fieldExtraInfo,
        description = this.fieldDescription,
        rules = this.fieldRules
    )
}

// Private helper functions to avoid duplication

private data class PriceCalculationResult(
    val originalPriceInCents: Long,
    val finalPriceInCents: Long,
    val totalDiscountInCents: Long
)

private fun MatchWithField.calculatePrices(): PriceCalculationResult {
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

    return PriceCalculationResult(originalPriceInCents, finalPriceInCents, totalDiscountInCents)
}

private fun MatchWithField.buildTeamSummary(): TeamSummaryResponse {
    val teamA = this.players.filter { it.team == TeamType.A }
    val teamB = this.players.filter { it.team == TeamType.B }

    val teamASummary = TeamPlayersSummary(
        playerCount = teamA.size,
        players = teamA.map { PlayerSummary(it.userId, it.avatarUrl, it.gender) }
    )
    val teamBSummary = TeamPlayersSummary(
        playerCount = teamB.size,
        players = teamB.map { PlayerSummary(it.userId, it.avatarUrl, it.gender) }
    )
    return TeamSummaryResponse(teamASummary, teamBSummary)
}

private fun MatchWithField.buildLocation(): Location? {
    return if (this.fieldLatitude != null && this.fieldLongitude != null) {
        Location(
            id = null,
            address = this.fieldAddress,
            city = this.fieldCity,
            country = this.fieldCountry,
            latitude = this.fieldLatitude,
            longitude = this.fieldLongitude
        )
    } else {
        null
    }
}