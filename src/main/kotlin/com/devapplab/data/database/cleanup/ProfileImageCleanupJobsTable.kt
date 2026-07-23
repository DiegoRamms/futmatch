package com.devapplab.data.database.cleanup

import com.devapplab.model.cleanup.ProfileImageCleanupStatus
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object ProfileImageCleanupJobsTable : Table("profile_image_cleanup_jobs") {
    val id = javaUUID("id").autoGenerate().uniqueIndex()
    val publicId = varchar("public_id", 512).uniqueIndex()
    val status = enumerationByName("status", 20, ProfileImageCleanupStatus::class)
    val attemptCount = integer("attempt_count").default(0)
    val lastError = text("last_error").nullable()
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    val completedAt = long("completed_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
