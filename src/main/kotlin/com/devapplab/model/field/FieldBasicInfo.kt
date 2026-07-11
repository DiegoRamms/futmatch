package com.devapplab.model.field

import java.math.BigDecimal
import java.util.*

data class FieldBasicInfo(
    val id: UUID,
    val name: String,
    val price: BigDecimal,
    val capacity: Int
)
