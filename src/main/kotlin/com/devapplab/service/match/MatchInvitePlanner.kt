package com.devapplab.service.match

import com.devapplab.model.match.MatchInviteCandidate
import com.devapplab.model.match.MatchInviteInfo
import com.devapplab.model.match.MatchInviteUserEligibility
import com.devapplab.model.match.MatchStatus
import com.devapplab.model.match.response.MatchInviteCheck
import com.devapplab.model.user.UserRole
import com.devapplab.model.user.UserStatus
import java.util.*
import kotlin.time.Duration.Companion.hours

/**
 * Pure decisions for match invites, kept apart from I/O so they are easy to test.
 */
internal object MatchInvitePlanner {

    /** Roles that receive invites. Admins are still skipped for matches they run or supervise. */
    val INVITE_ROLES = listOf(UserRole.PLAYER, UserRole.ADMIN)

    /**
     * Milestones (in hours before kick-off) that are due and not yet processed.
     * Only the smallest one is "sent"; any larger ones are consumed along with it,
     * so a match created 3h before kick-off gets one invite, not three.
     * Milestones larger than one already dispatched are skipped, so adding a new,
     * earlier milestone (e.g. 48h) never re-invites for a match that already got a later one.
     * Returns an empty set when nothing is due.
     */
    fun dueMilestones(
        matchDateTime: Long,
        now: Long,
        milestonesHours: List<Int>,
        dispatched: Set<Int>,
    ): Set<Int> {
        val timeUntilMatch = matchDateTime - now
        if (timeUntilMatch <= 0) return emptySet()
        return pendingMilestones(milestonesHours, dispatched)
            .filter { timeUntilMatch <= it.hours.inWholeMilliseconds }
            .toSet()
    }

    /**
     * Milestones still pending for a match: not dispatched and smaller than any dispatched one.
     * They become due one by one as kick-off gets closer.
     */
    fun pendingMilestones(milestonesHours: List<Int>, dispatched: Set<Int>): List<Int> {
        val smallestDispatched = dispatched.minOrNull() ?: Int.MAX_VALUE
        return milestonesHours.filter { it !in dispatched && it < smallestDispatched }.sortedDescending()
    }

    /**
     * Every condition a single user and match must meet to get an invite, in pipeline order.
     * Mirrors [assignInvites] and the candidate query, so the admin diagnosis and the job agree.
     * Does not cover "only the soonest match per cycle": a user due for two matches gets the sooner one.
     */
    fun diagnose(
        match: MatchInviteInfo,
        matchStatus: MatchStatus,
        user: MatchInviteUserEligibility?,
        activePlayers: Set<UUID>,
        milestonesHours: List<Int>,
        dispatched: Set<Int>,
        now: Long,
        dailyWindowMs: Long,
    ): List<MatchInviteCheck> {
        val pending = pendingMilestones(milestonesHours, dispatched)
        val checks = mutableListOf(
            MatchInviteCheck("match_scheduled", matchStatus == MatchStatus.SCHEDULED, "status=$matchStatus"),
            MatchInviteCheck("match_upcoming", match.dateTime > now),
            MatchInviteCheck("match_has_spots", match.availableSpots > 0, "availableSpots=${match.availableSpots}"),
            MatchInviteCheck(
                "milestone_pending",
                pending.isNotEmpty(),
                "milestones=$milestonesHours dispatched=${dispatched.sortedDescending()} pending=$pending"
            ),
        )
        if (user == null) {
            checks += MatchInviteCheck("user_found", false)
            return checks
        }
        val nextInviteAllowedAt = user.lastInviteAt?.plus(dailyWindowMs)
        checks += listOf(
            MatchInviteCheck("role_allowed", user.role in INVITE_ROLES, "role=${user.role} allowed=$INVITE_ROLES"),
            MatchInviteCheck("status_active", user.status == UserStatus.ACTIVE, "status=${user.status}"),
            MatchInviteCheck("notifications_enabled", user.notificationsEnabled),
            MatchInviteCheck("match_invites_enabled", user.matchInvitesEnabled),
            MatchInviteCheck("has_push_token", user.hasPushToken, "needs an active device with fcm_token"),
            MatchInviteCheck("not_match_admin", user.userId != match.adminId),
            MatchInviteCheck("not_match_supervisor", user.userId != match.supervisorId),
            MatchInviteCheck("not_in_match", user.userId !in activePlayers),
            MatchInviteCheck(
                "gender_and_level_visible",
                MatchVisibilityRules.isVisibleFor(user.gender, user.level, match.genderType, match.playerLevel),
                "user=${user.gender}/${user.level} match=${match.genderType}/${match.playerLevel}"
            ),
            MatchInviteCheck(
                "no_invite_last_24h",
                nextInviteAllowedAt == null || nextInviteAllowedAt <= now,
                user.lastInviteAt?.let { "lastInviteAt=$it nextAllowedAt=$nextInviteAllowedAt" }
            ),
        )
        return checks
    }

    /**
     * Gives each candidate at most one match: the soonest one they can join
     * (ties go to the match with fewer spots left). Skips users already invited
     * in the daily window, users already in the match, the match's admin/supervisor,
     * and matches the user can't see because of gender or level.
     */
    fun assignInvites(
        matches: List<MatchInviteInfo>,
        candidates: List<MatchInviteCandidate>,
        activePlayersByMatch: Map<UUID, Set<UUID>>,
        alreadyInvited: Set<UUID>,
    ): Map<UUID, List<MatchInviteCandidate>> {
        val ordered = matches
            .filter { it.availableSpots > 0 }
            .sortedWith(compareBy<MatchInviteInfo> { it.dateTime }.thenBy { it.availableSpots })
        if (ordered.isEmpty()) return emptyMap()

        val result = linkedMapOf<UUID, MutableList<MatchInviteCandidate>>()
        candidates
            .asSequence()
            .filter { it.userId !in alreadyInvited }
            .forEach { candidate ->
                val match = ordered.firstOrNull { match ->
                    candidate.userId != match.adminId &&
                        candidate.userId != match.supervisorId &&
                        candidate.userId !in activePlayersByMatch[match.matchId].orEmpty() &&
                        MatchVisibilityRules.isVisibleFor(
                            userGender = candidate.gender,
                            userLevel = candidate.level,
                            matchGenderType = match.genderType,
                            matchLevel = match.playerLevel,
                        )
                } ?: return@forEach
                result.getOrPut(match.matchId) { mutableListOf() }.add(candidate)
            }
        return result
    }
}
