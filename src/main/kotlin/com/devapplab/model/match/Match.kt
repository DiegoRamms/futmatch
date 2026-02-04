package com.devapplab.model.match

import com.devapplab.model.user.PlayerLevel
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
    val discountIds: List<UUID>? = null,
    val status: MatchStatus = MatchStatus.SCHEDULED,
    val genderType: GenderType,
    val playerLevel: PlayerLevel,
    val createdAt: Long = System.currentTimeMillis(),
)