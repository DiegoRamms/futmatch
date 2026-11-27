package com.devapplab.model.match

import com.devapplab.model.user.Gender
import com.devapplab.model.user.PlayerLevel
import com.devapplab.model.user.UserStatus
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

/** Everything that decides whether one user can receive invites (used by the admin diagnosis). */
data class MatchInviteUserEligibility(
    val userId: UUID,
    val status: UserStatus,
    val notificationsEnabled: Boolean,
    val matchInvitesEnabled: Boolean,
    val hasPushToken: Boolean,
    val gender: Gender,
    val level: PlayerLevel,
    /** created_at of the user's latest MATCH_INVITE, or null if never invited. */
    val lastInviteAt: Long?,
)

/** A match in any status, for the admin diagnosis. */
data class MatchInviteMatchState(
    val info: MatchInviteInfo,
    val status: MatchStatus,
)
