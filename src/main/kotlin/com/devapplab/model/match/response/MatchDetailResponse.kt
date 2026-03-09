package com.devapplab.model.match.response

import com.devapplab.model.field.FieldType
import com.devapplab.model.field.FootwearType
import com.devapplab.model.location.Location
import com.devapplab.model.match.GenderType
import com.devapplab.model.match.MatchStatus
import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class MatchDetailResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val fieldName: String,
    val fieldImageUrl: String?,
    val startTime: Long,
    val endTime: Long,
    val originalPriceInCents: Long,
    val totalDiscountInCents: Long,
    val priceInCents: Long, // Final price
    val genderType: GenderType,
    val status: MatchStatus,
    val availableSpots: Int,
    val teams: TeamSummaryResponse,
    val location: Location?,
    val footwearType: FootwearType?,
    val fieldType: FieldType?,
    val hasParking: Boolean,
    val extraInfo: String?,
    val description: String,
    val rules: String
)