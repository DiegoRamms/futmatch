package com.devapplab.model.match

import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class MatchPlayerGoal(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    @Serializable(with = UUIDSerializer::class)
    val matchId: UUID,
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID,
    val goalsCount: Int = 0,
)

@Serializable
data class PlayerGoalInput(
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID,
    val goals: Int,
    val isBestPlayer: Boolean = false,
)

data class CompleteMatchRequest(
    val goals: List<PlayerGoalInput>,
)
