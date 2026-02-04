package com.devapplab.model.match.request

import com.devapplab.model.match.GenderType
import com.devapplab.model.match.MatchStatus
import com.devapplab.model.user.PlayerLevel
import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class CreateMatchRequest(
    @Serializable(with = UUIDSerializer::class)
    val fieldId: UUID,
    val dateTime: Long,
    val dateTimeEnd: Long,
    val maxPlayers: Int,
    val minPlayersRequired: Int,
    val matchPriceInCents: Long,
    val discountIds: List<@Serializable(with = UUIDSerializer::class) UUID>? = null,
    val status: MatchStatus = MatchStatus.SCHEDULED,
    val genderType: GenderType = GenderType.MIXED,
    val playerLevel: PlayerLevel = PlayerLevel.ANY,
)