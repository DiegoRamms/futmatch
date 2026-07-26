package com.devapplab.model.match

import kotlin.math.roundToInt

/**
 * Aggregated match results for a user. This is shared by Home and profile endpoints.
 */
data class MatchWinStats(
    val playedMatches: Int,
    val wonMatches: Int,
    val decisiveMatches: Int = playedMatches
) {
    /**
     * OVR is based only on wins and losses; draws do not affect the result.
     */
    val overallScore: Int
        get() = if (decisiveMatches == 0) {
            0
        } else {
            ((wonMatches.toDouble() / decisiveMatches.toDouble()) * 100.0).roundToInt()
        }
}
