package com.devapplab.data.repository.match

import com.devapplab.data.database.match.MatchDao
import data.database.match.MatchWithFieldDao
import model.match.Match
import model.match.MatchBaseInfo
import model.match.MatchWithFieldBaseInfo
import java.util.*

class MatchRepositoryImp(private val matchDao: MatchDao, private val matchWithFieldDao: MatchWithFieldDao): MatchRepository {
    override suspend fun create(match: Match): MatchBaseInfo {
       return matchDao.createMatch(match)
    }

    override suspend fun getMatchesByFieldId(fieldId: UUID): List<MatchWithFieldBaseInfo> {
        return matchWithFieldDao.getMatchesWithFieldByFieldId(fieldId)
    }

    override suspend fun cancelMatch(matchId: UUID): Boolean {
        return matchDao.cancelMatch(matchId)
    }
}