package com.devapplab.data.database.password_reset

import com.devapplab.data.database.user.UserTable
import org.jetbrains.exposed.sql.Table

object PasswordResetTokensTable : Table("password_reset_tokens") {
    val token = varchar("token", 256)
    val userId = uuid("user_id").references(UserTable.id)
    val expiresAt = long("expires_at")

    override val primaryKey = PrimaryKey(token)
}
