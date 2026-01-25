package data.database.field

import com.devapplab.data.database.user.UserTable
import com.devapplab.model.user.FIELD_NAME_MAX_LENGTH
import data.database.location.LocationsTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object FieldTable : Table("field") {
    val id = uuid("id").autoGenerate().uniqueIndex()
    val name = varchar("name", FIELD_NAME_MAX_LENGTH).uniqueIndex()
    val locationId = uuid("location_id").references(LocationsTable.id).nullable()
    val pricePerPlayer = decimal("price_per_player", 10, 2)
    val capacity = integer("capacity")
    val adminId = uuid("admin_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    val description = text("description")
    val rules = text("rules")
    val createdAt = long("created_at").default(System.currentTimeMillis())
    val updatedAt = long("updated_at").default(System.currentTimeMillis())

    override val primaryKey = PrimaryKey(id)
}