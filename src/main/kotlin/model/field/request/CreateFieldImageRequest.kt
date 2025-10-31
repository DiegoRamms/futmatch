package com.devapplab.model.field.request

import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class CreateFieldImageRequest(
    @Serializable(with = UUIDSerializer::class)
    val fieldId: UUID,
    val position: Int
)
