package com.devapplab.model.field.response

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
    val location: Location? = null
)