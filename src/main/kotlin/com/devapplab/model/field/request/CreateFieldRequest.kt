package com.devapplab.model.field.request

import com.devapplab.model.field.FieldType
import com.devapplab.model.field.FootwearType
import kotlinx.serialization.Serializable

@Serializable
data class CreateFieldRequest(
    val name: String,
    val priceInCents: Long,
    val capacity: Int,
    val description: String,
    val rules: String,
    val footwearType: FootwearType? = null,
    val fieldType: FieldType? = null,
    val hasParking: Boolean = false,
    val extraInfo: String? = null
)