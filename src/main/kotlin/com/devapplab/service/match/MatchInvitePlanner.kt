package com.devapplab.service.match

import com.devapplab.model.match.MatchInviteCandidate
import com.devapplab.model.match.MatchInviteInfo
import java.util.*
import kotlin.time.Duration.Companion.hours

/**
 * Pure decisions for match invites, kept apart from I/O so they are easy to test.
 */
internal object MatchInvitePlanner {

    /**
     * Milestones (in hours before kick-off) that are due and not yet processed.
     * Only the smallest one is "sent"; any larger ones are consumed along with it,
     * so a match created 3h before kick-off gets one invite, not three.
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
        return milestonesHours
            .filter { it !in dispatched && timeUntilMatch <= it.hours.inWholeMilliseconds }
            .toSet()
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
