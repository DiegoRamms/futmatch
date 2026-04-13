package com.devapplab.model.match

import java.math.BigDecimal
import java.util.UUID

data class HomeSuggestedMatch(
    val matchId: UUID,
    val fieldId: UUID,
    val fieldName: String,
    val startTime: Long,
    val endTime: Long,
    val price: BigDecimal,
    val cityCode: String?,
    val fieldImageKey: String?
)

data class HomeNextMatch(
    val matchId: UUID,
    val fieldId: UUID,
    val fieldName: String,
    val startTime: Long,
    val address: String?,
    val fieldImageKey: String?
)

data class HomeLastMatch(
    val matchId: UUID,
    val fieldId: UUID,
    val fieldName: String,
    val playedAt: Long,
    val teamAScore: Int,
    val teamBScore: Int,
    val outcome: HomeMatchOutcome
)

data class HomeWinStats(
    val playedMatches: Int,
    val wonMatches: Int
)

enum class HomeMatchOutcome {
    WIN,
    LOSS,
    DRAW
}
