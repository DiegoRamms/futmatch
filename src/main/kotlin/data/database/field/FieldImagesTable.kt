package data.database.field

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object FieldImagesTable : Table("field_images") {
    val id = uuid("id").autoGenerate().uniqueIndex()
    val fieldId = uuid("field_id").references(FieldTable.id, onDelete = ReferenceOption.CASCADE)
    val imageUrl = text("image_path")
    val position = integer("position")
    val createdAt = long("created_at").default(System.currentTimeMillis())

    override val primaryKey = PrimaryKey(id)
}