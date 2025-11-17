package com.devapplab.data.database.field

import com.devapplab.data.database.user.UserTable
import data.database.field.FieldTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object FieldAdminsTable : Table("field_admins") {
    val fieldId = uuid("field_id").references(FieldTable.id, onDelete = ReferenceOption.CASCADE)
    val adminId = uuid("admin_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(fieldId, adminId)
}