package com.devapplab.data.database.discount

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.*

class DiscountDAO(id: EntityID<UUID>) : UUIDEntity(id) {

    companion object : UUIDEntityClass<DiscountDAO>(DiscountsTable)

    var code by DiscountsTable.code
    var description by DiscountsTable.description
    var discountType by DiscountsTable.discountType
    var value by DiscountsTable.value
    var validFrom by DiscountsTable.validFrom
    var validTo by DiscountsTable.validTo
    var isActive by DiscountsTable.isActive

    var createdAt by DiscountsTable.createdAt
    var updatedAt by DiscountsTable.updatedAt
}
