package com.devapplab.model.user.response

import com.devapplab.model.match.HomeMatchOutcome
import com.devapplab.model.user.PlayerLevel
import com.devapplab.model.user.PlayerPosition
import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ProfileStatsResponse(
    val matchesPlayed: Int,
    val matchesWon: Int,
    val mvpCount: Int,
    val totalGoals: Int
)

@Serializable
data class ProfileBaseResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val name: String,
    val lastName: String,
    val country: String,
    val playerPosition: PlayerPosition,
    val profilePic: String?,
    val level: PlayerLevel,
    val averageScore: Int,
    val stats: ProfileStatsResponse
)

@Serializable
data class ProfileMeResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val name: String,
    val lastName: String,
    val country: String,
    val playerPosition: PlayerPosition,
    val profilePic: String?,
    val level: PlayerLevel,
    val averageScore: Int,
    val stats: ProfileStatsResponse
)

@Serializable
data class ProfileLastMatchResponse(
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

@Serializable
data class ProfilePublicResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val name: String,
    val lastName: String,
    val country: String,
    val playerPosition: PlayerPosition,
    val profilePic: String?,
    val level: PlayerLevel,
    val averageScore: Int,
    val stats: ProfileStatsResponse,
    val lastMatch: ProfileLastMatchResponse?
)
