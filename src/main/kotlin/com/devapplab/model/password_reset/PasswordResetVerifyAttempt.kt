package com.devapplab.model.password_reset

import java.util.UUID

data class PasswordResetVerifyAttempt(
    val id: UUID,
    val attempts: Int,
    val lastAttemptAt: Long,
    val lockedUntil: Long?,
    val createdAt: Long,
    val updatedAt: Long
)
