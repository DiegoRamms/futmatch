package com.devapplab.model.match

import java.math.BigDecimal
import java.util.*

data class MatchWithFieldBaseInfo(
    val matchId: UUID,
    val fieldId: UUID,
    val fieldName: String,
    val fieldLocation: String,
    val matchDateTime: Long,
    val matchDateTimeEnd: Long,
    val matchPrice: BigDecimal,
    val discount: BigDecimal,
    val maxPlayers: Int,
    val minPlayersRequired: Int,
    val status: MatchStatus
)