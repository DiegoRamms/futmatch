package com.devapplab.data.database.mfa

import com.devapplab.data.database.device.DeviceTable
import com.devapplab.data.database.user.UserTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object LoginMfaChallengeTable : Table("login_mfa_challenges") {
    val tokenHash = varchar("token_hash", 64)
    val userId = javaUUID("user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    val deviceId = javaUUID("device_id").references(DeviceTable.id, onDelete = ReferenceOption.CASCADE)
    val expiresAt = long("expires_at")
    val createdAt = long("created_at")
    val usedAt = long("used_at").nullable()
    val revokedAt = long("revoked_at").nullable()

    override val primaryKey = PrimaryKey(tokenHash)
}
