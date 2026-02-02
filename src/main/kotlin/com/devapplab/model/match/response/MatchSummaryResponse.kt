package com.devapplab.model.match.response

import com.devapplab.model.location.Location
import com.devapplab.model.match.MatchStatus
import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class MatchSummaryResponse(
    @Serializable(with = UUIDSerializer::class)
    val matchId: UUID,
    val fieldName: String,
    val location: Location?,
    val date: Long,
    val time: Long,
    val priceInCents: Long,
    val distanceInKm: Double?,
    val status: MatchStatus
)