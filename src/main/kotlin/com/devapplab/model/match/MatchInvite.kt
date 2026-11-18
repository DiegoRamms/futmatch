package com.devapplab.model.match

import com.devapplab.model.user.Gender
import com.devapplab.model.user.PlayerLevel
import java.util.*

/** Upcoming match that may receive "join this match" invites. */
data class MatchInviteInfo(
    val matchId: UUID,
    val fieldName: String,
    val dateTime: Long,
    val maxPlayers: Int,
    val enrolledPlayers: Int,
    val genderType: GenderType,
    val playerLevel: PlayerLevel,
    val adminId: UUID,
    val supervisorId: UUID?,
) {
    val availableSpots: Int get() = (maxPlayers - enrolledPlayers).coerceAtLeast(0)
}

/** User who can receive invites: active, opted in, with at least one push token. */
data class MatchInviteCandidate(
    val userId: UUID,
    val localeTag: String,
    val gender: Gender,
    val level: PlayerLevel,
)

/** Invite ready to be persisted in the PLAYER inbox. */
data class MatchInviteNotification(
    val userId: UUID,
    val title: String,
    val body: String,
    val metadata: String,
)
