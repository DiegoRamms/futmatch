package com.devapplab.model.match

import com.devapplab.model.location.Location
import java.math.BigDecimal
import java.util.*

data class MatchWithFieldBaseInfo(
    val matchId: UUID,
    val fieldId: UUID,
    val fieldName: String,
    val fieldLocation: Location?,
    val matchDateTime: Long,
    val matchDateTimeEnd: Long,
    val matchPrice: BigDecimal,
    val discount: BigDecimal,
    val maxPlayers: Int,
    val minPlayersRequired: Int,
    val status: MatchStatus
)