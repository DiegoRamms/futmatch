package com.devapplab.data.database.pending_registrations

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object RegistrationVerifyAttemptTable : Table("registration_verify_attempts") {
    val id = javaUUID("id").autoGenerate().uniqueIndex()
    val emailLookup = varchar("email_lookup", 64).nullable().uniqueIndex()

    val attempts = integer("attempts").default(0)
    val lastAttemptAt = long("last_attempt_at")
    val lockedUntil = long("locked_until").nullable()

    val createdAt = long("created_at").default(System.currentTimeMillis())
    val updatedAt = long("updated_at").default(System.currentTimeMillis())

    override val primaryKey = PrimaryKey(id)
}
