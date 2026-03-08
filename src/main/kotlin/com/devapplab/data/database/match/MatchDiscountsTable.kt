package com.devapplab.data.database.match

import com.devapplab.data.database.discount.DiscountsTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object MatchDiscountsTable : Table("match_discounts") {
    val matchId = javaUUID("match_id").references(MatchTable.id, onDelete = ReferenceOption.CASCADE)
    val discountId = javaUUID("discount_id").references(DiscountsTable.id, onDelete = ReferenceOption.RESTRICT)

    override val primaryKey = PrimaryKey(matchId, discountId)
}
