package com.devapplab.data.repository

import com.devapplab.model.auth.RefreshTokenRecord
import com.devapplab.model.auth.RefreshTokenValidationInfo
import java.util.*

interface RefreshTokenRepository {
    fun saveToken(userId: UUID, deviceId: UUID, token: String, expiresAt: Long): UUID
    suspend fun findLatestTokenByUserId(userId: UUID): RefreshTokenRecord?
    fun getValidationInfo(deviceId: UUID): RefreshTokenValidationInfo?
    fun revokeToken(deviceId: UUID): Boolean
    fun revokeCurrentToken(deviceId: UUID): Boolean
    suspend fun deleteRevokedTokens(): Boolean
}


