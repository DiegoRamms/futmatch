package com.devapplab.model.auth

import com.devapplab.model.auth.identity.AuthProvider
import java.util.UUID

data class SocialLinkAttempt(
    val id: UUID,
    val tokenHash: String,
    val userId: UUID,
    val provider: AuthProvider,
    val issuer: String,
    val providerSubject: String,
    val mfaCodeId: UUID?,
    val passwordVerifiedAt: Long?,
    val expiresAt: Long,
    val usedAt: Long?,
    val createdAt: Long
)
