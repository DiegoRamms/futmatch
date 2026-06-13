package com.devapplab.model.mfa

import java.util.UUID

data class LoginMfaChallenge(
    val tokenHash: String,
    val userId: UUID,
    val deviceId: UUID,
    val expiresAt: Long,
    val createdAt: Long,
    val usedAt: Long?,
    val revokedAt: Long?
)
