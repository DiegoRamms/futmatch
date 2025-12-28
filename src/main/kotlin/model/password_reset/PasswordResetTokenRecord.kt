package com.devapplab.model.password_reset

import java.util.UUID

data class PasswordResetTokenRecord(
    val token: String,
    val userId: UUID,
    val expiresAt: Long
)
