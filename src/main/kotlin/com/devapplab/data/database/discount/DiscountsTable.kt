package com.devapplab.data.database.discount

import org.jetbrains.exposed.sql.Table

enum class DiscountType {
    PERCENTAGE,
    FIXED_AMOUNT
}

object DiscountsTable : Table("discounts") {
    val id = uuid("id").autoGenerate().uniqueIndex()
    val code = varchar("code", 50).uniqueIndex().nullable() // Para cupones
    val description = text("description")
    val discountType = enumerationByName("discount_type", 20, DiscountType::class)
    val value = decimal("value", 10, 2)
    val validFrom = long("valid_from").nullable()
    val validTo = long("valid_to").nullable()
    val isActive = bool("is_active").default(true)

    override val primaryKey = PrimaryKey(id)
}
