package com.devapplab.service.match

import com.devapplab.data.repository.discount.DiscountRepository
import com.devapplab.data.repository.match.MatchRepository
import com.devapplab.model.AppResult
import com.devapplab.model.ErrorCode
import com.devapplab.model.discount.Discount
import com.devapplab.model.discount.DiscountType
import com.devapplab.model.match.*
import com.devapplab.model.match.mapper.toMatchSummaryResponse
import com.devapplab.model.match.mapper.toResponse
import com.devapplab.model.match.response.MatchResponse
import com.devapplab.model.match.response.MatchSummaryResponse
import com.devapplab.model.match.response.MatchWithFieldResponse
import com.devapplab.service.firebase.FirebaseService
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.createError
import io.ktor.http.*
import java.math.BigDecimal
import java.util.*
import kotlin.math.*

class MatchService(
    private val matchRepository: MatchRepository,
    private val discountRepository: DiscountRepository,
    private val firebaseService: FirebaseService
) {

    private companion object {
        const val EARTH_RADIUS_KM = 6371.0 // Radius of the Earth in kilometers
        const val OVERLAP_THRESHOLD_MS = 15 * 60 * 1000 // 15 minutes in milliseconds
    }

    suspend fun create(match: Match, locale: Locale): AppResult<MatchResponse> {
        if (isMatchOverlapping(match)) {
            return locale.createError(
                titleKey = StringResourcesKey.MATCH_OVERLAP_TITLE,
                descriptionKey = StringResourcesKey.MATCH_OVERLAP_DESCRIPTION,
                status = HttpStatusCode.Conflict,
                errorCode = ErrorCode.MATCH_OVERLAP
            )
        }

        val matchCreated = matchRepository.create(match)

        firebaseService.signalMatchUpdate(matchCreated.id.toString()) // Signal Firebase update


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

    private suspend fun isMatchOverlapping(match: Match): Boolean {
        val existingTimeSlots = matchRepository.getMatchTimeSlotsByFieldId(match.fieldId)
        return existingTimeSlots.any { existingSlot ->
            val overlapStart = max(match.dateTime, existingSlot.dateTime)
            val overlapEnd = min(match.dateTimeEnd, existingSlot.dateTimeEnd)

            if (overlapStart < overlapEnd) {
                val overlapDuration = overlapEnd - overlapStart
                overlapDuration > OVERLAP_THRESHOLD_MS
            } else {
                false
            }
        }
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

        firebaseService.signalMatchUpdate(matchId.toString()) // Signal Firebase update

        return AppResult.Success(response)
    }

    suspend fun joinMatch(userId: UUID, matchId: UUID, team: TeamType?, locale: Locale): AppResult<Boolean> {
        val match = matchRepository.getMatchById(matchId)
            ?: return locale.createError(
                titleKey = StringResourcesKey.NOT_FOUND_TITLE,
                descriptionKey = StringResourcesKey.NOT_FOUND_DESCRIPTION,
                status = HttpStatusCode.NotFound,
                errorCode = ErrorCode.NOT_FOUND
            )

        if (match.status != MatchStatus.SCHEDULED) {
            return locale.createError(
                titleKey = StringResourcesKey.MATCH_NOT_SCHEDULED_TITLE,
                descriptionKey = StringResourcesKey.MATCH_NOT_SCHEDULED_DESCRIPTION,
                status = HttpStatusCode.Conflict,
                errorCode = ErrorCode.MATCH_NOT_SCHEDULED
            )
        }

        if (matchRepository.isUserInMatch(matchId, userId)) {
            return locale.createError(
                titleKey = StringResourcesKey.MATCH_ALREADY_JOINED_TITLE,
                descriptionKey = StringResourcesKey.MATCH_ALREADY_JOINED_DESCRIPTION,
                status = HttpStatusCode.Conflict,
                errorCode = ErrorCode.ALREADY_EXISTS
            )
        }

        if (match.players.size >= match.maxPlayers) {
            return locale.createError(
                titleKey = StringResourcesKey.MATCH_FULL_TITLE,
                descriptionKey = StringResourcesKey.MATCH_FULL_DESCRIPTION,
                status = HttpStatusCode.Conflict,
                errorCode = ErrorCode.MATCH_FULL
            )
        }

        val teamToJoin = if (team != null) {
            val maxPerTeam = match.maxPlayers / 2
            val currentTeamCount = match.players.count { it.team == team }
            
            if (currentTeamCount >= maxPerTeam) {
                 return locale.createError(
                    titleKey = StringResourcesKey.MATCH_TEAM_FULL_TITLE,
                    descriptionKey = StringResourcesKey.MATCH_TEAM_FULL_DESCRIPTION,
                    status = HttpStatusCode.Conflict,
                    errorCode = ErrorCode.MATCH_TEAM_FULL
                )
            }
            team
        } else {
            val teamA = match.players.count { it.team == TeamType.A }
            val teamB = match.players.count { it.team == TeamType.B }
            if (teamA <= teamB) TeamType.A else TeamType.B
        }

        val joined = matchRepository.addPlayerToMatch(matchId, userId, teamToJoin)

        if (joined) {
            firebaseService.signalMatchUpdate(matchId.toString())
            return AppResult.Success(true)
        } else {
            return locale.createError(
                titleKey = StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                descriptionKey = StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY,
                status = HttpStatusCode.InternalServerError,
                errorCode = ErrorCode.GENERAL_ERROR
            )
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lon1Rad = Math.toRadians(lon1)
        val lat2Rad = Math.toRadians(lat2)
        val lon2Rad = Math.toRadians(lon2)

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