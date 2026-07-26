package com.devapplab.model.match

import com.devapplab.model.user.PlayerLevel
import java.math.BigDecimal
import java.util.UUID

data class HomeSuggestedMatch(
    val matchId: UUID,
    val fieldId: UUID,
    val fieldName: String,
    val startTime: Long,
    val endTime: Long,
    val price: BigDecimal,
    val genderType: GenderType,
    val playerLevel: PlayerLevel,
    val cityCode: String?,
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

enum class HomeMatchOutcome {
    WIN,
    LOSS,
    DRAW
}
