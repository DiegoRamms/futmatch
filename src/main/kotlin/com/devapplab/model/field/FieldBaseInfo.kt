package com.devapplab.model.field

import com.devapplab.model.location.Location
import java.util.*


data class FieldBaseInfo(
    val id: UUID,
    val name: String,
    val locationId: UUID?,
    val price: Double,
    val capacity: Int,
    val description: String,
    val rules: String,
    val location: Location? = null
)