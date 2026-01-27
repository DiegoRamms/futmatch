package com.devapplab.model.match.response

import com.devapplab.model.match.MatchStatus
import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class MatchResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @Serializable(with = UUIDSerializer::class)
    val fieldId: UUID,
    val dateTime: Long,
    val dateTimeEnd: Long,
    val maxPlayers: Int,
    val minPlayersRequired: Int,
    val matchPriceInCents: Long,
    val discountPriceInCents: Long,
    val status: MatchStatus
)
