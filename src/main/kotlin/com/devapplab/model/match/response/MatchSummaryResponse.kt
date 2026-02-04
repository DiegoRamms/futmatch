package com.devapplab.model.match.response

import com.devapplab.model.match.GenderType
import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class MatchSummaryResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val fieldName: String,
    val fieldImageUrl: String?,
    val startTime: Long,
    val endTime: Long,
    val originalPriceInCents: Long,
    val totalDiscountInCents: Long,
    val priceInCents: Long, // Final price
    val genderType: GenderType,
    val availableSpots: Int,
    val distanceKm: Double?,
    val teams: TeamSummaryResponse
)

@Serializable
data class TeamSummaryResponse(
    val teamA: TeamPlayersSummary,
    val teamB: TeamPlayersSummary
)

@Serializable
data class TeamPlayersSummary(
    val playerCount: Int,
    val players: List<PlayerSummary>
)

@Serializable
data class PlayerSummary(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val avatarUrl: String?
)
