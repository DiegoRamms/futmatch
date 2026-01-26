package com.devapplab.data.database.field

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption

object FieldImagesTable : UUIDTable("field_images") {
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
}