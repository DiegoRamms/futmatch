package com.devapplab.model.field.request

import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class UpdateFieldRequest(
    @Serializable(with = UUIDSerializer::class)
    val fieldId: UUID,
    val name: String,
    @Serializable(with = UUIDSerializer::class)
    val locationId: UUID? = null,
    val priceInCents: Long,
    val capacity: Int,
    val description: String,
    val rules: String
)