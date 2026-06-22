package com.devapplab.model.match.request

import com.devapplab.model.match.TeamType
import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class RebalanceMatchTeamsRequest(
    val players: List<RebalanceMatchPlayerRequest>
)

@Serializable
data class RebalanceMatchPlayerRequest(
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID,
    val team: TeamType
)
