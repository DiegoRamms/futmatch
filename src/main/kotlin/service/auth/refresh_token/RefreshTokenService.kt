package com.devapplab.service.auth.refresh_token

import com.devapplab.model.auth.RefreshTokenPayload
import java.util.*

interface RefreshTokenService {
    fun generateRefreshToken(): RefreshTokenPayload
    suspend fun saveRefreshToken(userId: UUID, deviceId: UUID, refreshTokenPayload: RefreshTokenPayload)
    suspend fun revokeRefreshToken(deviceId: UUID): Boolean
    fun isValidRefreshToken(refreshTokenPayload: RefreshTokenPayload): Boolean
}
