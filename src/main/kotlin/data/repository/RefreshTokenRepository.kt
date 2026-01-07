package com.devapplab.data.repository

import com.devapplab.model.auth.RefreshTokenPayload
import model.auth.RefreshTokenRecord
import model.auth.RefreshTokenValidationInfo
import java.util.*

interface RefreshTokenRepository {
    suspend fun saveToken(userId: UUID, deviceId: UUID, refreshTokenPayload: RefreshTokenPayload): UUID
    suspend fun findLatestTokenByUserId(userId: UUID): RefreshTokenRecord?
    fun getValidationInfo(deviceId: UUID): RefreshTokenValidationInfo?
    suspend fun deleteRevokedTokens(): Boolean
}


