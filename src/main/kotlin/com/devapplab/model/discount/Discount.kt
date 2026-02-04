package com.devapplab.model.discount

import java.math.BigDecimal
import java.util.UUID

data class Discount(
    val id: UUID,
    val code: String?,
    val description: String,
    val discountType: DiscountType,
    val value: BigDecimal,
    val validFrom: Long?,
    val validTo: Long?,
    val isActive: Boolean
)
