package com.devapplab.model.location

import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Location(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID = UUID.randomUUID(),
    val address: String?,
    val city: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double,
)
