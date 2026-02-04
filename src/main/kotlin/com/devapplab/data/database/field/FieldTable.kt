package com.devapplab.data.database.field

import com.devapplab.data.database.location.LocationsTable
import com.devapplab.data.database.user.UserTable
import com.devapplab.model.field.FieldType
import com.devapplab.model.field.FootwearType
import com.devapplab.model.user.FIELD_NAME_MAX_LENGTH
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object FieldTable : Table("fields") {
    val id = uuid("id").autoGenerate().uniqueIndex()
    val name = varchar("name", FIELD_NAME_MAX_LENGTH).uniqueIndex()
    val locationId = uuid("location_id").references(LocationsTable.id).nullable()
    val pricePerPlayer = decimal("price_per_player", 10, 2)
    val capacity = integer("capacity")
    val adminId = uuid("admin_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    val description = text("description")
    val rules = text("rules")
    val footwearType = enumerationByName("footwear_type", 20, FootwearType::class).nullable()
    val fieldType = enumerationByName("field_type", 20, FieldType::class).nullable()
    val hasParking = bool("has_parking").default(false)
    val extraInfo = text("extra_info").nullable()
    val createdAt = long("created_at").default(System.currentTimeMillis())
    val updatedAt = long("updated_at").default(System.currentTimeMillis())

    override val primaryKey = PrimaryKey(id)
}