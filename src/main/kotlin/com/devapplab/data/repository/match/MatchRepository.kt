package com.devapplab.data.repository.match

import com.devapplab.model.match.*
import java.util.*

interface MatchRepository {
    suspend fun create(match: Match): MatchBaseInfo
    suspend fun getMatchesByFieldId(fieldId: UUID): List<MatchWithFieldBaseInfo>
    suspend fun getMatchTimeSlotsByFieldId(fieldId: UUID): List<MatchTimeSlot>
    suspend fun getAllMatches(): List<MatchWithFieldBaseInfo>
    suspend fun getUpcomingMatches(): List<MatchWithFieldBaseInfo>
    suspend fun cancelMatch(matchId: UUID): Boolean
    suspend fun updateMatch(matchId: UUID, match: Match): Boolean
    suspend fun getPublicMatches(): List<MatchWithField>
    suspend fun getMatchById(matchId: UUID): MatchWithField?
    suspend fun addPlayerToMatch(matchId: UUID, userId: UUID, team: TeamType): Boolean
    suspend fun removePlayerFromMatch(matchId: UUID, userId: UUID): Boolean
    suspend fun isUserInMatch(matchId: UUID, userId: UUID): Boolean
    suspend fun getMatchPlayerId(matchId: UUID, userId: UUID): UUID?
    suspend fun updatePlayerStatus(matchPlayerId: UUID, status: MatchPlayerStatus): Boolean
    suspend fun getExpiredReservations(expirationTime: Long): List<ExpiredReservation>
    suspend fun hasActiveReservation(userId: UUID): Boolean
}