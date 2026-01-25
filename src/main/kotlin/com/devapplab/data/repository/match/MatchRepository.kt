package com.devapplab.data.repository.match

import com.devapplab.model.match.Match
import com.devapplab.model.match.MatchBaseInfo
import com.devapplab.model.match.MatchWithFieldBaseInfo
import java.util.*

interface MatchRepository {
    suspend fun create(match: Match): MatchBaseInfo
    suspend fun getMatchesByFieldId(fieldId: UUID): List<MatchWithFieldBaseInfo>
    suspend fun cancelMatch(matchId: UUID): Boolean
}