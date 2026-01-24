package data.database.match

import data.database.discount.DiscountsTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object MatchDiscountsTable : Table("match_discounts") {
    val matchId = uuid("match_id").references(MatchTable.id, onDelete = ReferenceOption.CASCADE)
    val discountId = uuid("discount_id").references(DiscountsTable.id, onDelete = ReferenceOption.RESTRICT)

    override val primaryKey = PrimaryKey(matchId, discountId)
}
