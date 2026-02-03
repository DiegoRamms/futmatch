package com.devapplab.data.database.discount

import com.devapplab.data.database.match.MatchDiscountsTable
import com.devapplab.data.database.match.MatchTable
import com.devapplab.data.database.user.UserTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object UserMatchDiscountsTable : Table("user_match_discounts") {
    val userId = uuid("user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    val matchId = uuid("match_id").references(MatchTable.id, onDelete = ReferenceOption.CASCADE)
    val discountId = uuid("discount_id").references(DiscountsTable.id, onDelete = ReferenceOption.RESTRICT)
    val appliedAt = long("applied_at").default(System.currentTimeMillis())

    override val primaryKey = PrimaryKey(userId, matchId, discountId)
}
