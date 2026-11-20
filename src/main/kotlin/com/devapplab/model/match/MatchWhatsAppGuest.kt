package com.devapplab.model.match

import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class MatchWhatsAppGuest(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val name: String,
    val team: TeamType
)

@Serializable
data class AddMatchWhatsAppGuestRequest(val name: String, val team: TeamType)

enum class MatchSeatChange {
    SUCCESS, MATCH_NOT_FOUND, NOT_SCHEDULED, MATCH_FULL, TEAM_FULL, ALREADY_JOINED, GUEST_NOT_FOUND
}
