package com.devapplab.model.match.request

import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import model.match.MatchStatus
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
    val discountInCents: Long = 0,
    val status: MatchStatus = MatchStatus.SCHEDULED
)