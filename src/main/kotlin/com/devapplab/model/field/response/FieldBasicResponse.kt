package com.devapplab.model.field.response

import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class FieldBasicResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val name: String
)
