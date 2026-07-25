package com.devapplab.data.database.device

import com.devapplab.data.database.user.UserTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object DesktopDeviceTable : Table("desktop_devices") {
    // It is deliberately the same identifier used by DeviceTable, JWT and refresh tokens.
    val id = javaUUID("id").references(DeviceTable.id, onDelete = ReferenceOption.CASCADE)
    val publicKey = text("public_key").uniqueIndex()
    val label = varchar("label", 120)
    val approvedByUserId = javaUUID("approved_by_user_id").references(UserTable.id, onDelete = ReferenceOption.RESTRICT)
    val isActive = bool("is_active").default(true)
    val createdAt = long("created_at")
    val revokedAt = long("revoked_at").nullable()
    override val primaryKey = PrimaryKey(id)
}
