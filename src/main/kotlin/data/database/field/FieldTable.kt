package data.database.field

import com.devapplab.data.database.user.UserTable
import com.devapplab.model.field.FIELD_NAME_MAX_LENGTH
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object FieldTable : Table("field") {
    val id = uuid("id").autoGenerate().uniqueIndex()
    val name = varchar("name", FIELD_NAME_MAX_LENGTH).uniqueIndex()
    val location = text("location")
    val price = decimal("price", 10, 2) // Price per Player
    val capacity = integer("capacity")
    val adminId = uuid("admin_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    val description = text("description")
    val rules = text("rules")
    val createdAt = long("created_at").default(System.currentTimeMillis())
    val updatedAt = long("updated_at").default(System.currentTimeMillis())

    override val primaryKey = PrimaryKey(id)
}