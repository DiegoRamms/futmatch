package com.devapplab.data.database.field

import com.devapplab.data.database.user.UserTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object FieldAdminsTable : Table("field_admins") {
    val fieldId = javaUUID("field_id").references(FieldTable.id, onDelete = ReferenceOption.CASCADE)
    val adminId = javaUUID("admin_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(fieldId, adminId)
}