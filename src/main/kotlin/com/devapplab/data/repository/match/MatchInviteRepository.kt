package com.devapplab.data.repository.match

import com.devapplab.model.match.MatchInviteCandidate
import com.devapplab.model.match.MatchInviteInfo
import com.devapplab.model.match.MatchInviteNotification
import java.util.*

interface MatchInviteRepository {
    /** SCHEDULED matches starting in (from, to] with their enrolled-player count. */
    suspend fun getMatchesForInvite(from: Long, to: Long): List<MatchInviteInfo>

    suspend fun getDispatchedMilestones(matchIds: List<UUID>): Map<UUID, Set<Int>>

    /** RESERVED/JOINED players per match. */
    suspend fun getActivePlayerIds(matchIds: List<UUID>): Map<UUID, Set<UUID>>

    /** Active players who opted in to invites and have at least one active push token. */
    suspend fun getInviteCandidates(): List<MatchInviteCandidate>

    /** Users that already got a MATCH_INVITE at or after [since]. */
    suspend fun getUsersInvitedSince(since: Long): Set<UUID>

    suspend fun getActiveFcmTokensByUserIds(userIds: Collection<UUID>): Map<UUID, List<String>>

    /**
     * Must run inside a transaction. Locks the match row, re-checks that it is still
     * SCHEDULED, upcoming, has free spots and that none of [milestonesHours] were
     * processed. Then stores the milestones and the notifications atomically.
     * Returns false when another run already handled it or the match is no longer eligible.
     */
    fun saveMatchInvitesTx(
        matchId: UUID,
        milestonesHours: Set<Int>,
        notifications: List<MatchInviteNotification>,
        sentAt: Long,
    ): Boolean
}
