package com.devapplab.model.match

import java.math.BigDecimal
import java.util.*

data class Match(
    val id: UUID = UUID.randomUUID(),
    val fieldId: UUID,
    val adminId: UUID,
    val dateTime: Long,
    val dateTimeEnd: Long,
    val maxPlayers: Int,
    val minPlayersRequired: Int,
    val matchPrice: BigDecimal,
    val discount: BigDecimal = BigDecimal.ZERO,
    val status: MatchStatus = MatchStatus.SCHEDULED,
    val createdAt: Long = System.currentTimeMillis(),
)