package com.devapplab.data.repository.match

import model.match.Match
import model.match.MatchBaseInfo
import model.match.MatchWithFieldBaseInfo
import java.util.UUID

interface MatchRepository {
    suspend fun create(match: Match): MatchBaseInfo
    suspend fun getMatchesByFieldId(fieldId: UUID): List<MatchWithFieldBaseInfo>
    suspend fun cancelMatch(matchId: UUID): Boolean
}