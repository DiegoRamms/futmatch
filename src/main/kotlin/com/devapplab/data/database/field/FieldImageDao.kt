package com.devapplab.data.database.field

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.*

class FieldImageDao(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<FieldImageDao>(FieldImagesTable)

    var fieldId by FieldImagesTable.fieldId
    var key by FieldImagesTable.key
    var mime by FieldImagesTable.mime
    var sizeBytes by FieldImagesTable.sizeBytes
    var width by FieldImagesTable.width
    var height by FieldImagesTable.height
    var isPrimary by FieldImagesTable.isPrimary
    var position by FieldImagesTable.position
    var createdAt by FieldImagesTable.createdAt
    var updatedAt by FieldImagesTable.updatedAt
}
