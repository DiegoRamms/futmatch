package com.devapplab.data.database.discount

import com.devapplab.data.database.match.MatchTable
import com.devapplab.data.database.user.UserTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object UserMatchDiscountsTable : Table("user_match_discounts") {
    val userId = javaUUID("user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    val matchId = javaUUID("match_id").references(MatchTable.id, onDelete = ReferenceOption.CASCADE)
    val discountId = javaUUID("discount_id").references(DiscountsTable.id, onDelete = ReferenceOption.RESTRICT)
    val appliedAt = long("applied_at").clientDefault { System.currentTimeMillis() }

    override val primaryKey = PrimaryKey(userId, matchId, discountId)
}
