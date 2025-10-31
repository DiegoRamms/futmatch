package data.database.field

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object FieldImagesTable : Table("field_images") {
    val id = uuid("id").autoGenerate().uniqueIndex()
    val fieldId = uuid("field_id").references(FieldTable.id, onDelete = ReferenceOption.CASCADE)

    val key = text("key")

    val mime = varchar("mime_type", 100).nullable()
    val sizeBytes = long("size_bytes").default(0)
    val width = integer("width").nullable()
    val height = integer("height").nullable()

    val isPrimary = bool("is_primary").default(false)
    val position = integer("position").default(0)

    // Timestamps en epoch millis
    val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }
    val updatedAt = long("updated_at").clientDefault { System.currentTimeMillis() }


    override val primaryKey = PrimaryKey(id)
}