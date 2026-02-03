package com.devapplab.service.match

import com.devapplab.data.repository.discount.DiscountRepository
import com.devapplab.data.repository.match.MatchRepository
import com.devapplab.model.AppResult
import com.devapplab.model.discount.Discount
import com.devapplab.model.discount.DiscountType
import com.devapplab.model.match.Match
import com.devapplab.model.match.MatchWithField
import com.devapplab.model.match.TeamType
import com.devapplab.model.match.mapper.toResponse
import com.devapplab.model.match.response.*
import java.lang.Math.toRadians
import java.math.BigDecimal
import java.util.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MatchService(
    private val matchRepository: MatchRepository,
    private val discountRepository: DiscountRepository
) {

    private companion object {
        const val EARTH_RADIUS_KM = 6371.0 // Radius of the Earth in kilometers
    }

    suspend fun create(match: Match): AppResult<MatchResponse> {
        val matchCreated = matchRepository.create(match)

        val discounts = match.discountIds?.let {
            if (it.isNotEmpty()) discountRepository.getDiscountsByIds(it) else emptyList()
        } ?: emptyList()

        val totalDiscount = calculateTotalDiscount(matchCreated.matchPrice, discounts)

        val response = MatchResponse(
            id = matchCreated.id,
            fieldId = matchCreated.fieldId,
            dateTime = matchCreated.dateTime,
            dateTimeEnd = matchCreated.dateTimeEnd,
            maxPlayers = matchCreated.maxPlayers,
            minPlayersRequired = matchCreated.minPlayersRequired,
            matchPriceInCents = (matchCreated.matchPrice * BigDecimal(100)).toLong(),
            discountPriceInCents = (totalDiscount * BigDecimal(100)).toLong(),
            status = matchCreated.status,
            genderType = matchCreated.genderType,
            playerLevel = matchCreated.playerLevel
        )
        return AppResult.Success(response)
    }

    suspend fun getMatchesByFieldId(fieldId: UUID): AppResult<List<MatchWithFieldResponse>> {
        val matches = matchRepository.getMatchesByFieldId(fieldId).map { it.toResponse() }
        return AppResult.Success(matches)
    }

    suspend fun getAllMatches(): AppResult<List<MatchWithFieldResponse>> {
        val matches = matchRepository.getAllMatches().map { it.toResponse() }
        return AppResult.Success(matches)
    }

    suspend fun getPlayerMatches(userLat: Double?, userLon: Double?): AppResult<List<MatchSummaryResponse>> {
        val matchesWithField = matchRepository.getPublicMatches()

        val response = matchesWithField.map { match ->
            val distance = if (userLat != null && userLon != null && match.fieldLatitude != null && match.fieldLongitude != null) {
                calculateDistance(userLat, userLon, match.fieldLatitude, match.fieldLongitude)
            } else {
                null
            }
            match.toMatchSummaryResponse(distance)
        }

        val sortedResponse = response.sortedWith(
            compareBy<MatchSummaryResponse> { it.startTime }
                .thenBy { it.distanceKm ?: Double.MAX_VALUE }
        )

        return AppResult.Success(sortedResponse)
    }

    suspend fun cancelMatch(matchUuid: UUID): AppResult<Boolean> {
        return AppResult.Success(matchRepository.cancelMatch(matchUuid))
    }

    suspend fun updateMatch(matchId: UUID, match: Match): AppResult<MatchResponse> {
        matchRepository.updateMatch(matchId, match)
        val updatedMatch = matchRepository.getMatchById(matchId) // Re-fetch to get all data
            ?: throw IllegalStateException("Match not found after update") // Or handle as an error

        val totalDiscount = calculateTotalDiscount(updatedMatch.matchPrice, updatedMatch.discounts)

        val response = MatchResponse(
            id = updatedMatch.matchId,
            fieldId = updatedMatch.fieldId,
            dateTime = updatedMatch.dateTime,
            dateTimeEnd = updatedMatch.dateTimeEnd,
            maxPlayers = updatedMatch.maxPlayers,
            minPlayersRequired = updatedMatch.minPlayersRequired,
            matchPriceInCents = (updatedMatch.matchPrice * BigDecimal(100)).toLong(),
            discountPriceInCents = (totalDiscount * BigDecimal(100)).toLong(),
            status = updatedMatch.status,
            genderType = updatedMatch.genderType,
            playerLevel = updatedMatch.playerLevel
        )

        return AppResult.Success(response)
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat1Rad = toRadians(lat1)
        val lon1Rad = toRadians(lon1)
        val lat2Rad = toRadians(lat2)
        val lon2Rad = toRadians(lon2)

        val deltaLat = lat2Rad - lat1Rad
        val deltaLon = lon2Rad - lon1Rad

        val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(deltaLon / 2) * sin(deltaLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS_KM * c
    }

    private fun calculateTotalDiscount(originalPrice: BigDecimal, discounts: List<Discount>): BigDecimal {
        var finalPrice = originalPrice
        discounts.forEach { discount ->
            finalPrice = when (discount.discountType) {
                DiscountType.FIXED_AMOUNT -> finalPrice - discount.value
                DiscountType.PERCENTAGE -> finalPrice * (BigDecimal.ONE - discount.value.divide(BigDecimal(100)))
            }
        }
        if (finalPrice < BigDecimal.ZERO) {
            finalPrice = BigDecimal.ZERO
        }
        return originalPrice - finalPrice
    }
}

fun MatchWithField.toMatchSummaryResponse(distanceKm: Double?): MatchSummaryResponse {
    val teamAPlayers = this.players.filter { it.team == TeamType.A }.map { PlayerSummary(it.userId, it.avatarUrl) }
    val teamBPlayers = this.players.filter { it.team == TeamType.B }.map { PlayerSummary(it.userId, it.avatarUrl) }

    val totalTeamAPlayers = teamAPlayers.size
    val totalTeamBPlayers = teamBPlayers.size

    val totalPlayersInMatch = totalTeamAPlayers + totalTeamBPlayers
    val availableSpots = this.maxPlayers - totalPlayersInMatch

    // Calculate final price with discounts
    val originalPrice = this.matchPrice
    var finalPrice = originalPrice

    this.discounts.forEach { discount ->
        finalPrice = when (discount.discountType) {
            DiscountType.FIXED_AMOUNT -> finalPrice - discount.value
            DiscountType.PERCENTAGE -> finalPrice * (BigDecimal.ONE - discount.value.divide(BigDecimal(100)))
        }
    }
    if (finalPrice < BigDecimal.ZERO) {
        finalPrice = BigDecimal.ZERO
    }

    val totalDiscount = originalPrice - finalPrice

    return MatchSummaryResponse(
        id = this.matchId,
        fieldName = this.fieldName,
        fieldImageUrl = this.fieldImageUrl,
        startTime = this.dateTime,
        endTime = this.dateTimeEnd,
        originalPriceInCents = (originalPrice * BigDecimal(100)).toLong(),
        totalDiscountInCents = (totalDiscount * BigDecimal(100)).toLong(),
        priceInCents = (finalPrice * BigDecimal(100)).toLong(),
        genderType = this.genderType,
        availableSpots = availableSpots,
        distanceKm = distanceKm,
        teams = TeamSummaryResponse(
            teamA = TeamPlayersSummary(
                playerCount = totalTeamAPlayers,
                players = teamAPlayers
            ),
            teamB = TeamPlayersSummary(
                playerCount = totalTeamBPlayers,
                players = teamBPlayers
            )
        )
    )
}