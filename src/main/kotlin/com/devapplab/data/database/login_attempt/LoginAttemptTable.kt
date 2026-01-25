package com.devapplab.data.database.login_attempt

import com.devapplab.model.user.USER_EMAIL_MAX_LENGTH
import org.jetbrains.exposed.dao.id.UUIDTable

object LoginAttemptTable : UUIDTable("login_attempt") {
    val email = varchar("email", USER_EMAIL_MAX_LENGTH).index()

    val attempts = integer("attempts").default(0)
    val lastAttemptAt = long("last_attempt_at")
    val lockedUntil = long("locked_until").nullable()

    val createdAt = long("created_at").default(System.currentTimeMillis())
    val updatedAt = long("updated_at").default(System.currentTimeMillis())
}
