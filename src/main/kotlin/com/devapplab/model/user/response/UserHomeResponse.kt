package com.devapplab.model.user.response

import com.devapplab.model.match.HomeMatchOutcome
import com.devapplab.model.user.PlayerLevel
import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class UserHomeResponse(
    val profile: HomeProfileSection,
    val nextMatch: HomeNextMatchSection?,
    val suggestedMatches: List<HomeSuggestedMatchSection>,
    val lastMatch: HomeLastMatchSection?
)

@Serializable
data class HomeProfileSection(
    val greetingName: String,
    val level: PlayerLevel,
    val averageScore: Int,
    val profileImageUrl: String?
)

@Serializable
data class HomeNextMatchSection(
    @Serializable(with = UUIDSerializer::class)
    val matchId: UUID,
    @Serializable(with = UUIDSerializer::class)
    val fieldId: UUID,
    val fieldName: String,
    val startTime: Long,
    val address: String?,
    val imageUrl: String?
)

@Serializable
data class HomeSuggestedMatchSection(
    @Serializable(with = UUIDSerializer::class)
    val matchId: UUID,
    @Serializable(with = UUIDSerializer::class)
    val fieldId: UUID,
    val fieldName: String,
    val startTime: Long,
    val endTime: Long,
    val priceInCents: Long,
    val imageUrl: String?
)

@Serializable
data class HomeLastMatchSection(
    @Serializable(with = UUIDSerializer::class)
    val matchId: UUID,
    @Serializable(with = UUIDSerializer::class)
    val fieldId: UUID,
    val fieldName: String,
    val playedAt: Long,
    val outcome: HomeMatchOutcome,
    val teamAScore: Int,
    val teamBScore: Int
)
