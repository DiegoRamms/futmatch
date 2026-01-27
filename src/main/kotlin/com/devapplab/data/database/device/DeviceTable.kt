package com.devapplab.data.database.device

import com.devapplab.data.database.user.UserTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object DeviceTable : Table("devices") {
    val id = uuid("id").autoGenerate().uniqueIndex()
    val userId = uuid("user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    val deviceInfo = text("device_info")

    val isTrusted = bool("is_trusted").default(false)
    val isActive = bool("is_active").default(true)

    val lastUsedAt = long("last_used_at").default(System.currentTimeMillis())
    val createdAt = long("created_at").default(System.currentTimeMillis())

    override val primaryKey = PrimaryKey(id)
}