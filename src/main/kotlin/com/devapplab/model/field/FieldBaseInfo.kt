package com.devapplab.model.field

import com.devapplab.model.location.Location
import java.math.BigDecimal
import java.util.*

data class FieldBaseInfo(
    val id: UUID,
    val name: String,
    val locationId: UUID?,
    val price: BigDecimal,
    val capacity: Int,
    val description: String,
    val rules: String,
    val footwearType: FootwearType?,
    val fieldType: FieldType?,
    val hasParking: Boolean,
    val extraInfo: String?,
    val location: Location?
)