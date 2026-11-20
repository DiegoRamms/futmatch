package com.devapplab.model.match

import com.devapplab.model.discount.Discount
import com.devapplab.model.field.FieldImageBaseInfo
import com.devapplab.model.field.FieldType
import com.devapplab.model.field.FootwearType
import com.devapplab.model.user.Gender
import com.devapplab.model.user.PlayerLevel
import java.math.BigDecimal
import java.util.UUID

// This class will hold the combined data from the database query
data class MatchWithField(
    val matchId: UUID,
    val fieldId: UUID,
    val adminId: UUID,
    /** Organizer assigned to run the match; `null` means the creator (`adminId`) runs it. */
    val supervisorId: UUID? = null,
    val dateTime: Long,
    val dateTimeEnd: Long,
    val maxPlayers: Int,
    val minPlayersRequired: Int,
    val matchPrice: BigDecimal,
    val status: MatchStatus,
    val playerLevel: PlayerLevel,
    val genderType: GenderType,
    val createdAt: Long,
    val updatedAt: Long,
    val fieldName: String, // From FieldTable
    val fieldLatitude: Double?, // From LocationsTable
    val fieldLongitude: Double?, // From LocationsTable
    val fieldAddress: String?, // From LocationsTable
    val fieldCityCode: String?, // From LocationsTable
    val fieldCountryCode: String?, // From LocationsTable
    val fieldFootwearType: FootwearType?, // From FieldTable
    val fieldType: FieldType?, // From FieldTable
    val fieldHasParking: Boolean, // From FieldTable
    val fieldExtraInfo: String?, // From FieldTable
    val fieldDescription: String, // From FieldTable
    val fieldRules: String, // From FieldTable
    val teamAScore: Int? = null,
    val teamBScore: Int? = null,
    val bestPlayerId: UUID? = null,
    val fieldImages: List<FieldImageBaseInfo> = emptyList(), // All images for the field
    val teamVisuals: MatchTeamVisuals? = null,
    val players: List<MatchPlayerInfo>, // From MatchPlayersTable and UserTable
    val discounts: List<Discount>, // From MatchDiscountsTable and DiscountsTable
    val whatsappGuests: List<MatchWhatsAppGuest> = emptyList()
) {
    val activeAppPlayers: List<MatchPlayerInfo>
        get() = players.filter { it.status == MatchPlayerStatus.JOINED || it.status == MatchPlayerStatus.RESERVED }

    val canCompleteWithoutMvp: Boolean
        get() = status == MatchStatus.PENDING_RESULT && whatsappGuests.isNotEmpty() && activeAppPlayers.isEmpty()

    fun guestCount(team: TeamType): Int = whatsappGuests.count { it.team == team }

    fun occupiedSeats(team: TeamType): Int = activeAppPlayers.count { it.team == team } + guestCount(team)
}

data class MatchPlayerInfo(
    val userId: UUID,
    val team: TeamType,
    val gender: Gender,
    val country: String,
    val name: String,
    val avatarUrl: String?, // From UserTable
    val status: MatchPlayerStatus = MatchPlayerStatus.RESERVED,
    val attendanceStatus: AttendanceStatus? = null,
    val joinedAt: Long // From MatchPlayersTable
)
