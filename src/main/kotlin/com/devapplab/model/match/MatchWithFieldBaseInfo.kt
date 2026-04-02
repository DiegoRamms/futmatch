package com.devapplab.model.match

import com.devapplab.model.field.FieldImageBaseInfo
import com.devapplab.model.field.FieldType
import com.devapplab.model.field.FootwearType
import com.devapplab.model.location.Location
import com.devapplab.model.user.PlayerLevel
import java.math.BigDecimal
import java.util.*

data class MatchWithFieldBaseInfo(
    val matchId: UUID,
    val fieldId: UUID,
    val fieldName: String,
    val fieldLocation: Location?,
    val matchDateTime: Long,
    val matchDateTimeEnd: Long,
    val matchPrice: BigDecimal,
    val maxPlayers: Int,
    val minPlayersRequired: Int,
    val status: MatchStatus,
    val footwearType: FootwearType?,
    val fieldType: FieldType?,
    val hasParking: Boolean,
    val fieldImages: List<FieldImageBaseInfo> = emptyList(),
    val genderType: GenderType,
    val playerLevel: PlayerLevel
)
