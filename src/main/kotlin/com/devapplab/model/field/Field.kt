package com.devapplab.model.field

import java.math.BigDecimal
import java.util.UUID

data class Field(
    val id: UUID? = null,
    val name: String,
    val locationId: UUID? = null,
    val price: BigDecimal,
    val capacity: Int,
    val adminId: UUID,
    val description: String,
    val rules: String,
    val footwearType: FootwearType? = null,
    val fieldType: FieldType? = null,
    val hasParking: Boolean = false,
    val extraInfo: String? = null,
    val createdAt: Long ? = null,
    val updatedAt: Long ? = null
)