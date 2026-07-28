package com.devapplab.model.auth

import java.util.UUID

data class RegistrationVerifyAttempt(
    val id: UUID,
    val attempts: Int,
    val lastAttemptAt: Long,
    val lockedUntil: Long?,
    val createdAt: Long,
    val updatedAt: Long
)
