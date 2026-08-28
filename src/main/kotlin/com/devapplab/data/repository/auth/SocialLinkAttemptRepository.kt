package com.devapplab.data.repository.auth

import com.devapplab.model.auth.SocialLinkAttempt
import com.devapplab.model.auth.identity.AuthProvider
import java.util.UUID

interface SocialLinkAttemptRepository {
    fun createTx(tokenHash: String, userId: UUID, provider: AuthProvider, issuer: String, subject: String, expiresAt: Long, now: Long): SocialLinkAttempt
    fun findValidByTokenHashTx(tokenHash: String, now: Long): SocialLinkAttempt?
    fun setMfaCodeTx(id: UUID, mfaCodeId: UUID, passwordVerifiedAt: Long): Boolean
    fun consumeTx(id: UUID, now: Long): Boolean
    suspend fun deleteInactive(now: Long): Boolean
}
