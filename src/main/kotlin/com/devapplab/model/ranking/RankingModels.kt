package com.devapplab.model.ranking

import com.devapplab.model.user.Gender
import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class RankingCategory { VICTORIES, GOALS }

@Serializable
data class RankingPeriod(val year: Int, val month: Int, val startTime: Long, val endTimeExclusive: Long, val timeZone: String = "UTC")

@Serializable
data class RankingCommunity(val id: String = "global", val name: String = "Global")

@Serializable
data class RankingPlayer(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val name: String, val avatarUrl: String?, val gender: Gender, val country: String?,
    val position: Int, val matchesPlayed: Int, val victories: Int, val goals: Int
)

@Serializable
data class RankingTarget(val position: Int, val victories: Int)

@Serializable
enum class RankingParticipation { RANKED, NOT_PARTICIPATING }

@Serializable
data class RankingSummary(
    val period: RankingPeriod, val community: RankingCommunity = RankingCommunity(),
    val category: RankingCategory = RankingCategory.VICTORIES, val status: RankingParticipation,
    val position: Int?, val victories: Int, val matchesPlayed: Int, val nextTarget: RankingTarget?, val calculatedAt: Long
)

@Serializable
data class RankingResponse(
    val period: RankingPeriod, val community: RankingCommunity = RankingCommunity(), val category: RankingCategory,
    val podium: List<RankingPlayer>, val players: List<RankingPlayer>, val currentUser: RankingPlayer?,
    val totalPlayers: Int, val page: Int, val pageSize: Int, val calculatedAt: Long
)

data class RankingMatchRow(
    val userId: UUID, val name: String, val avatar: String?, val gender: Gender, val country: String,
    val won: Boolean, val goals: Int
)
