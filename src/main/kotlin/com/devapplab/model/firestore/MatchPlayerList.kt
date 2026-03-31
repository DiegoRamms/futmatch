package com.devapplab.model.firestore

import com.devapplab.model.match.MatchPlayerStatus
import com.devapplab.model.match.TeamType
import com.devapplab.model.user.Gender
import com.google.cloud.firestore.FieldValue

/**
 * Represents the real-time player list for a single match, stored in a Firestore document.
 * This is a projection of the data in Postgres, optimized for client-side real-time updates.
 */
data class MatchPlayerList(
    val players: List<Player> = emptyList()
) {
    data class Player(
        val playerId: String,
        val name: String,
        val avatarUrl: String?,
        val gender: Gender,
        val team: TeamType,
        val status: MatchPlayerStatus,
        val country: String,
        val reservationExpiresAt: Long? = null
    )

    fun toMap(): Map<String, Any> {
        return mapOf(
            "players" to players.map { player ->
                mapOf(
                    "playerId" to player.playerId,
                    "name" to player.name,
                    "avatarUrl" to player.avatarUrl,
                    "gender" to player.gender.name,
                    "team" to player.team.name,
                    "status" to player.status.name,
                    "country" to player.country,
                    "reservationExpiresAt" to player.reservationExpiresAt
                )
            },
            "lastUpdated" to FieldValue.serverTimestamp()
        )
    }
}
