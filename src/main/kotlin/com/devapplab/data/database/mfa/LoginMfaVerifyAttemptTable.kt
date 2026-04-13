package com.devapplab.data.database.mfa

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object LoginMfaVerifyAttemptTable : Table("login_mfa_verify_attempts") {
    val id = javaUUID("id").autoGenerate().uniqueIndex()
    val lookupKey = varchar("lookup_key", 120).uniqueIndex()
    val userId = javaUUID("user_id")
    val deviceId = javaUUID("device_id")

    val attempts = integer("attempts").default(0)
    val lastAttemptAt = long("last_attempt_at")
    val lockedUntil = long("locked_until").nullable()

    val createdAt = long("created_at").default(System.currentTimeMillis())
    val updatedAt = long("updated_at").default(System.currentTimeMillis())

    override val primaryKey = PrimaryKey(id)
}

