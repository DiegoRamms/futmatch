package com.devapplab.model.cleanup

import java.util.UUID

enum class ProfileImageCleanupStatus {
    PENDING,
    COMPLETED
}

data class ProfileImageCleanupJob(
    val id: UUID,
    val publicId: String,
    val attemptCount: Int,
    val lastError: String?,
    val createdAt: Long
)
