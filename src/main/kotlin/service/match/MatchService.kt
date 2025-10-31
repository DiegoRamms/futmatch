package com.devapplab.service.match

import com.devapplab.data.repository.match.MatchRepository
import com.devapplab.model.AppResult
import com.devapplab.model.match.mapper.toMatch
import com.devapplab.model.match.mapper.toResponse
import com.devapplab.model.match.request.CreateMatchRequest
import com.devapplab.model.match.response.MatchResponse
import model.match.Match
import model.match.response.MatchWithFieldResponse
import java.util.UUID

class MatchService(private val matchRepository: MatchRepository) {
    suspend fun create(match: Match): AppResult<MatchResponse> {
        val matchCrated = matchRepository.create(match).toResponse()
        return AppResult.Success(matchCrated)
    }

    suspend fun getMatchesByFieldId(fieldId: UUID): AppResult<List<MatchWithFieldResponse>> {
        val matches = matchRepository.getMatchesByFieldId(fieldId).map { it.toResponse() }
        return AppResult.Success(matches)
    }

    suspend fun cancelMatch(matchUuid: UUID): AppResult<Boolean> {
        return AppResult.Success(matchRepository.cancelMatch(matchUuid))
    }
}