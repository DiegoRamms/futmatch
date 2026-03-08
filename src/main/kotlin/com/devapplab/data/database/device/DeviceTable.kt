package com.devapplab.data.database.device

import com.devapplab.data.database.user.UserTable
import com.devapplab.model.device.DevicePlatform
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object DeviceTable : Table("devices") {
    val id = uuid("id").autoGenerate().uniqueIndex()
    val userId = uuid("user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    val platform = enumerationByName("platform", 20, DevicePlatform::class)
    val deviceInfo = text("device_info").nullable()
    val fcmToken = text("fcm_token").nullable()
    val pushTokenUpdatedAt = long("push_token_updated_at").nullable()
    val appVersion = varchar("app_version", 50).nullable()
    val osVersion = varchar("os_version", 50).nullable()
    val isTrusted = bool("is_trusted").default(false)
    val isActive = bool("is_active").default(true)
    val lastUsedAt = long("last_used_at").clientDefault { System.currentTimeMillis() }
    val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }

    override val primaryKey = PrimaryKey(id)
}