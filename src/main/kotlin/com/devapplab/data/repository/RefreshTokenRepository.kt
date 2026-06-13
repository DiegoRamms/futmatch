package com.devapplab.data.repository

import com.devapplab.model.auth.RefreshTokenRecord
import java.util.*

interface RefreshTokenRepository {
    fun saveToken(userId: UUID, deviceId: UUID, token: String, expiresAt: Long): UUID
    fun findByTokenHash(tokenHash: String): RefreshTokenRecord?
    fun findActiveByDeviceId(deviceId: UUID): RefreshTokenRecord?
    fun revokeToken(deviceId: UUID): Boolean
    fun revokeCurrentToken(deviceId: UUID): Boolean
    suspend fun deleteRevokedTokens(): Boolean
}
