package com.devapplab.data.repository

import com.devapplab.model.auth.RefreshTokenPayload
import com.devapplab.model.auth.RefreshTokenRecord
import com.devapplab.model.auth.RefreshTokenValidationInfo
import java.util.*

interface RefreshTokenRepository {
    suspend fun saveToken(userId: UUID, deviceId: UUID, refreshTokenPayload: RefreshTokenPayload): UUID
    suspend fun findLatestTokenByUserId(userId: UUID): RefreshTokenRecord?
    fun getValidationInfo(deviceId: UUID): RefreshTokenValidationInfo?
    suspend fun deleteRevokedTokens(): Boolean
}


