package com.devapplab.data.database.discount

import com.devapplab.model.discount.DiscountType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID


object DiscountsTable : Table("discounts") {
    val id = javaUUID("id").autoGenerate().uniqueIndex()
    val code = varchar("code", 50).uniqueIndex().nullable()
    val description = text("description")
    val discountType = enumerationByName("discount_type", 20, DiscountType::class)
    val value = decimal("value", 10, 2)
    val validFrom = long("valid_from").nullable()
    val validTo = long("valid_to").nullable()
    val isActive = bool("is_active").default(true)

    val createdAt = long("created_at").clientDefault{System.currentTimeMillis()}
    val updatedAt = long("updated_at").clientDefault { System.currentTimeMillis()}

    override val primaryKey = PrimaryKey(id)
}
