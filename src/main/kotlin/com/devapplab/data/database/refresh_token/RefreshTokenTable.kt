package com.devapplab.data.database.refresh_token

import com.devapplab.data.database.device.DeviceTable
import com.devapplab.data.database.user.UserTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object RefreshTokenTable : Table("refresh_tokens") {
    val id = javaUUID("id").autoGenerate().uniqueIndex()
    val userId = javaUUID("user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    val deviceId = javaUUID("device_id").references(DeviceTable.id, onDelete = ReferenceOption.CASCADE)
    val token = varchar("token", 64).uniqueIndex()
    val expiresAt = long("expires_at")
    val createdAt = long("created_at").default(System.currentTimeMillis())
    val ipAddress = varchar("ip_address", 45).nullable()
    val userAgent = text("user_agent").nullable()
    val revoked = bool("revoked").default(false)
    val status = varchar("status", 32).default("ACTIVE")
    val statusReason = varchar("status_reason", 32).nullable()
    val revokedAt = long("revoked_at").nullable()

    init {
        index(false, deviceId, status, createdAt)
        index(false, expiresAt)
    }

    override val primaryKey = PrimaryKey(id)
}
