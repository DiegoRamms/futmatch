package com.devapplab.data.repository

import com.devapplab.model.auth.RefreshTokenRecord
import com.devapplab.model.auth.RefreshTokenStatus
import com.devapplab.model.auth.RefreshTokenStatusReason
import com.devapplab.model.auth.RefreshTokenValidationRecord
import java.util.*

interface RefreshTokenRepository {
    fun saveToken(userId: UUID, deviceId: UUID, token: String, expiresAt: Long): UUID
    fun findByTokenHash(tokenHash: String): RefreshTokenRecord?
    fun findValidationByTokenHash(tokenHash: String): RefreshTokenValidationRecord?
    fun markTokenAsRotatedIfActive(tokenId: UUID, changedAt: Long): Boolean
    fun markPreviousActiveTokensAsRotated(deviceId: UUID, currentTokenId: UUID, changedAt: Long): Boolean
    fun updateTokenStatus(
        tokenId: UUID,
        status: RefreshTokenStatus,
        reason: RefreshTokenStatusReason,
        changedAt: Long
    ): Boolean
    fun revokeActiveTokens(deviceId: UUID, reason: RefreshTokenStatusReason, changedAt: Long): Boolean
    fun revokeActiveTokensByUserId(userId: UUID, reason: RefreshTokenStatusReason, changedAt: Long): Int
    suspend fun deleteRevokedTokens(): Boolean
}
