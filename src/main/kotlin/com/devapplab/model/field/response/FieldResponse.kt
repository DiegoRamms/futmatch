package com.devapplab.model.field.response

import com.devapplab.model.field.FieldType
import com.devapplab.model.field.FootwearType
import com.devapplab.model.location.Location
import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class FieldResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val name: String,
    @Serializable(with = UUIDSerializer::class)
    val locationId: UUID?,
    val priceInCents: Long,
    val capacity: Int,
    val description: String,
    val rules: String,
    val footwearType: FootwearType? = null,
    val fieldType: FieldType? = null,
    val hasParking: Boolean = false,
    val extraInfo: String? = null,
    val location: Location? = null
)