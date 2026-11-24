package com.devapplab.model.match.response

import kotlinx.serialization.Serializable

/** One condition of the invite pipeline and whether it holds. */
@Serializable
data class MatchInviteCheck(
    val name: String,
    val passed: Boolean,
    val detail: String? = null,
)

/** Why a user would or would not get a MATCH_INVITE for a match. */
@Serializable
data class MatchInviteDiagnosisResponse(
    val matchId: String,
    val userId: String,
    /** True when every check passes: the user is invited on the next due milestone. */
    val eligible: Boolean,
    val checks: List<MatchInviteCheck>,
    /** Effective milestones (Remote Config or local, capped by the paid-join window). */
    val milestonesHours: List<Int>,
    val dispatchedMilestones: List<Int>,
    /** Milestones that the next cycle would process for this match. */
    val dueNowMilestones: List<Int>,
    /** Epoch millis when the next pending milestone becomes due, or null if none is left. */
    val nextMilestoneAt: Long?,
    val hoursUntilMatch: Double,
)

/** Result of one invite cycle, for logs and `POST /admin/match-invites/run`. */
@Serializable
data class MatchInviteCycleSummary(
    val milestonesHours: List<Int> = emptyList(),
    val matchesInRange: Int = 0,
    val matchesDue: Int = 0,
    val openMatchesDue: Int = 0,
    val candidates: Int = 0,
    val candidatesInvitedLast24h: Int = 0,
    val matches: List<MatchInviteCycleMatch> = emptyList(),
    /** Set when the cycle stopped early, e.g. "no matches in range". */
    val stoppedReason: String? = null,
)

@Serializable
data class MatchInviteCycleMatch(
    val matchId: String,
    val milestonesHours: List<Int>,
    val recipients: Int,
    val availableSpots: Int,
    /** "sent", "skipped" (another run handled it or the match changed) or "error". */
    val outcome: String,
)
