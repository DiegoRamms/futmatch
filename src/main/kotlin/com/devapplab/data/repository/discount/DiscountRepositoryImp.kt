package com.devapplab.data.repository.discount

import com.devapplab.config.dbQuery
import com.devapplab.data.database.discount.DiscountsTable
import com.devapplab.model.discount.Discount
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.util.*

class DiscountRepositoryImp : DiscountRepository {
    override suspend fun getDiscountsByIds(discountIds: List<UUID>): List<Discount> = dbQuery {
        DiscountsTable
            .selectAll().where { DiscountsTable.id inList discountIds and (DiscountsTable.isActive eq true) }
            .map { row ->
                Discount(
                    id = row[DiscountsTable.id],
                    code = row[DiscountsTable.code],
                    description = row[DiscountsTable.description],
                    discountType = row[DiscountsTable.discountType],
                    value = row[DiscountsTable.value],
                    validFrom = row[DiscountsTable.validFrom],
                    validTo = row[DiscountsTable.validTo],
                    isActive = row[DiscountsTable.isActive]
                )
            }
    }
}
