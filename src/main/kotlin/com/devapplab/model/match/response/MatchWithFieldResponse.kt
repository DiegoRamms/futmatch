package com.devapplab.model.match.response

import com.devapplab.model.field.FieldType
import com.devapplab.model.field.FootwearType
import com.devapplab.model.location.Location
import com.devapplab.model.match.GenderType
import com.devapplab.model.match.MatchStatus
import com.devapplab.model.user.PlayerLevel
import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class MatchWithFieldResponse(
    @Serializable(with = UUIDSerializer::class)
    val matchId: UUID,
    @Serializable(with = UUIDSerializer::class)
    val fieldId: UUID,
    val fieldName: String,
    val fieldLocation: Location?,
    val matchDateTime: Long,
    val matchDateTimeEnd: Long,
    val matchPriceInCents: Long,
    val discountInCents: Long,
    val maxPlayers: Int,
    val minPlayersRequired: Int,
    val status: MatchStatus,
    val footwearType: FootwearType?,
    val fieldType: FieldType?,
    val hasParking: Boolean,
    val mainImage: String?,
    val genderType: GenderType,
    val playerLevel: PlayerLevel
)