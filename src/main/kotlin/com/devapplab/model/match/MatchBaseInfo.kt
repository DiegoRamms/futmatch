package com.devapplab.model.match

import com.devapplab.model.user.PlayerLevel
import java.math.BigDecimal
import java.util.*

data class MatchBaseInfo(
    val id: UUID,
    val fieldId: UUID,
    val dateTime: Long,
    val dateTimeEnd: Long,
    val maxPlayers: Int,
    val minPlayersRequired: Int,
    val matchPrice: BigDecimal,
    val status: MatchStatus,
    val genderType: GenderType,
    val playerLevel: PlayerLevel
)