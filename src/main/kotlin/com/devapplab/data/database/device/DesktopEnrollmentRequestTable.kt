package com.devapplab.data.database.device

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

/** Server-issued, short-lived enrollment data. It deliberately has no user owner yet. */
object DesktopEnrollmentRequestTable : Table("desktop_enrollment_requests") {
    val id = javaUUID("id").autoGenerate().uniqueIndex()
    val deviceId = javaUUID("device_id").uniqueIndex()
    val publicKey = text("public_key").uniqueIndex()
    val label = varchar("label", 120)
    val nonce = javaUUID("nonce").uniqueIndex()
    val expiresAt = long("expires_at")
    val createdAt = long("created_at")

    init {
        index(false, expiresAt)
    }

    override val primaryKey = PrimaryKey(id)
}
