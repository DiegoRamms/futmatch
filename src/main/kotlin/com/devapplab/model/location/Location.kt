package com.devapplab.model.location

import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Location(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val address: String?,
    val countryCode: String?,
    val cityCode: String?,
    val latitude: Double,
    val longitude: Double,
)
