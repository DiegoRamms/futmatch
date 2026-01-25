package com.devapplab.model.auth

import java.util.*

data class RefreshTokenValidationInfo(
    val userId: UUID,
    val isEmailVerified: Boolean,
    val token: String,
    val expiresAt: Long,
    val createdAt: Long,
    val revoked: Boolean
)