package com.devapplab.model.team.response

import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class TeamResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val name: String,
    val color: String
)
