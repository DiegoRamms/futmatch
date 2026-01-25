package com.devapplab.data.database.refresh_token

import com.devapplab.data.database.device.DeviceTable
import com.devapplab.data.database.user.UserTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object RefreshTokenTable : Table("refresh_tokens") {
    val id = uuid("id").autoGenerate().uniqueIndex()
    val userId = uuid("user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    val deviceId = uuid("device_id").references(DeviceTable.id, onDelete = ReferenceOption.CASCADE)
    val token = varchar("token", 64)
    val expiresAt = long("expires_at")
    val createdAt = long("created_at").default(System.currentTimeMillis())
    val ipAddress = varchar("ip_address", 45).nullable()
    val userAgent = text("user_agent").nullable()
    val revoked = bool("revoked").default(false)

    override val primaryKey = PrimaryKey(id)
}