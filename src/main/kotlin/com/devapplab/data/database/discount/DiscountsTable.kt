package com.devapplab.data.database.discount

import com.devapplab.model.discount.DiscountType
import org.jetbrains.exposed.dao.id.UUIDTable

object DiscountsTable : UUIDTable("discounts") {
    val code = varchar("code", 50).uniqueIndex().nullable() // Para cupones
    val description = text("description")
    val discountType = enumerationByName("discount_type", 20, DiscountType::class)
    val value = decimal("value", 10, 2)
    val validFrom = long("valid_from").nullable()
    val validTo = long("valid_to").nullable()
    val isActive = bool("is_active").default(true)

    val createdAt = long("created_at").default(System.currentTimeMillis())
    val updatedAt = long("updated_at").default(System.currentTimeMillis())
}
