package com.devapplab.model.match

import com.devapplab.model.discount.Discount
import com.devapplab.model.field.FieldType
import com.devapplab.model.field.FootwearType
import com.devapplab.model.user.PlayerLevel
import java.math.BigDecimal
import java.util.UUID

// This class will hold the combined data from the database query
data class MatchWithField(
    val matchId: UUID,
    val fieldId: UUID,
    val adminId: UUID,
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
    val fieldCity: String?, // From LocationsTable
    val fieldCountry: String?, // From LocationsTable
    val fieldFootwearType: FootwearType?, // From FieldTable
    val fieldType: FieldType?, // From FieldTable
    val fieldHasParking: Boolean, // From FieldTable
    val fieldExtraInfo: String?, // From FieldTable
    val fieldImageUrl: String?, // Derived from FieldImageTable or system
    val players: List<MatchPlayerInfo>, // From MatchPlayersTable and UserTable
    val discounts: List<Discount> // From MatchDiscountsTable and DiscountsTable
)

data class MatchPlayerInfo(
    val userId: UUID,
    val team: TeamType,
    val avatarUrl: String? // From UserTable
)
