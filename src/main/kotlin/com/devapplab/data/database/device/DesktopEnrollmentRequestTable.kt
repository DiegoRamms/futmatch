package com.devapplab.data.database.device

import com.devapplab.data.database.user.UserTable
import com.devapplab.model.device.DesktopEnrollmentStatus
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

/** Temporary, mobile-admin approved enrollment requests. It is not a trusted device yet. */
object DesktopEnrollmentRequestTable : Table("desktop_enrollment_requests") {
    val id = javaUUID("id")
    val publicKey = text("public_key").uniqueIndex()
    val nonce = varchar("nonce", 36).uniqueIndex()
    val label = varchar("label", 120)
    val ownerUserId = javaUUID("owner_user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    val status = enumerationByName("status", 16, DesktopEnrollmentStatus::class)
    val submittedByUserId = javaUUID("submitted_by_user_id").references(UserTable.id, onDelete = ReferenceOption.RESTRICT)
    val approvedByUserId = javaUUID("approved_by_user_id").references(UserTable.id, onDelete = ReferenceOption.RESTRICT).nullable()
    val createdAt = long("created_at")
    val expiresAt = long("expires_at")
    val decidedAt = long("decided_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
