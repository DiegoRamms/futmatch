package com.devapplab.data.database.field

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.*

class FieldDao(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<FieldDao>(FieldTable)

    var name by FieldTable.name
    var locationId by FieldTable.locationId
    var pricePerPlayer by FieldTable.pricePerPlayer
    var capacity by FieldTable.capacity
    var adminId by FieldTable.adminId
    var description by FieldTable.description
    var rules by FieldTable.rules
    var createdAt by FieldTable.createdAt
    var updatedAt by FieldTable.updatedAt
}