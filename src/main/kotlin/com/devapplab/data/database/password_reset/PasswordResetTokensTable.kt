package com.devapplab.data.database.password_reset

import com.devapplab.data.database.user.UserTable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object PasswordResetTokensTable : Table("password_reset_tokens") {
    val token = varchar("token", 256)
    val userId = javaUUID("user_id").references(UserTable.id)
    val expiresAt = long("expires_at")

    override val primaryKey = PrimaryKey(token)
}
