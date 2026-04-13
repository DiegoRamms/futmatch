package com.devapplab.model.mfa

import java.util.UUID

data class LoginMfaVerifyAttempt(
    val id: UUID,
    val userId: UUID,
    val deviceId: UUID,
    val attempts: Int,
    val lastAttemptAt: Long,
    val lockedUntil: Long?,
    val createdAt: Long,
    val updatedAt: Long
)

