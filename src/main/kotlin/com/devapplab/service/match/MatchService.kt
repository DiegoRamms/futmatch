package com.devapplab.service.match

import com.devapplab.data.repository.match.MatchRepository
import com.devapplab.model.AppResult
import com.devapplab.model.match.Match
import com.devapplab.model.match.mapper.toResponse
import com.devapplab.model.match.mapper.toSummaryResponse
import com.devapplab.model.match.response.MatchResponse
import com.devapplab.model.match.response.MatchSummaryResponse
import com.devapplab.model.match.response.MatchWithFieldResponse
import java.util.*

class MatchService(private val matchRepository: MatchRepository) {
    suspend fun create(match: Match): AppResult<MatchResponse> {
        val matchCrated = matchRepository.create(match).toResponse()
        return AppResult.Success(matchCrated)
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
        val matches = matchRepository.getUpcomingMatches()

        val matchesWithDistance = matches.map { match ->
            val distance = if (userLat != null && userLon != null && match.fieldLocation != null) {
                calculateDistance(userLat, userLon, match.fieldLocation.latitude, match.fieldLocation.longitude)
            } else {
                null
            }
            match to distance
        }

        val sortedMatches = matchesWithDistance.sortedWith(
            compareBy<Pair<com.devapplab.model.match.MatchWithFieldBaseInfo, Double?>> { it.first.matchDateTime }
                .thenBy { it.second ?: Double.MAX_VALUE }
        )

        val response = sortedMatches.map { (match, distance) ->
            match.toSummaryResponse(distance)
        }

        return AppResult.Success(response)
    }

    suspend fun cancelMatch(matchUuid: UUID): AppResult<Boolean> {
        return AppResult.Success(matchRepository.cancelMatch(matchUuid))
    }

    suspend fun updateMatch(matchId: UUID, match: Match): AppResult<Boolean> {
        return AppResult.Success(matchRepository.updateMatch(matchId, match))
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371 // Radius of the earth in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }
}