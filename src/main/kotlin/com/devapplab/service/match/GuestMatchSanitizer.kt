package com.devapplab.service.match

import com.devapplab.model.match.response.MatchDetailResponse
import com.devapplab.model.match.response.MatchSummaryResponse
import com.devapplab.model.match.response.TeamSummaryResponse

/**
 * Strips player data from match responses served to guests (no session).
 *
 * Guests may see that a match exists, where, when, its price and how many spots
 * are taken, but never who is playing: names, avatars, ids, countries, goal
 * scorers, best player and the organizer are removed. Team player counts are kept so the
 * availability is still accurate.
 */
object GuestMatchSanitizer {

    fun sanitize(summary: MatchSummaryResponse): MatchSummaryResponse =
        summary.copy(teams = sanitize(summary.teams))

    fun sanitize(detail: MatchDetailResponse): MatchDetailResponse =
        detail.copy(
            teams = sanitize(detail.teams),
            goalBreakdown = null,
            bestPlayer = null,
            organizer = null
        )

    private fun sanitize(teams: TeamSummaryResponse): TeamSummaryResponse =
        TeamSummaryResponse(
            teamA = teams.teamA.copy(players = emptyList()),
            teamB = teams.teamB.copy(players = emptyList())
        )
}
