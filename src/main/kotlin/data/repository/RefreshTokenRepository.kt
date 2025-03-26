package com.devapplab.data.repository

import model.auth.RefreshTokenRecord
import model.auth.RefreshTokenValidationInfo
import java.util.*

interface RefreshTokenRepository {
    suspend fun saveToken(userId: UUID, deviceId: UUID, token: String, expiresAt: Long): Boolean
    suspend fun findLatestTokenByUserId(userId: UUID): RefreshTokenRecord?
    suspend fun getValidationInfo(deviceId: UUID): RefreshTokenValidationInfo?
    suspend fun revokeToken(deviceId: UUID): Boolean
}


