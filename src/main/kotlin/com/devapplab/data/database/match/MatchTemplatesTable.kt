package com.devapplab.data.database.match

import com.devapplab.data.database.field.FieldTable
import com.devapplab.data.database.user.UserTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object MatchTemplatesTable : Table("match_templates") {
    val id = javaUUID("id").autoGenerate()
    val adminId = javaUUID("admin_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    val fieldId = javaUUID("field_id").references(FieldTable.id, onDelete = ReferenceOption.CASCADE)
    val settings = text("settings")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, adminId, updatedAt)
    }
}
