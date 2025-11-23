package com.devapplab.service.auth.refresh_token

import com.devapplab.model.auth.RefreshTokenPayload

interface RefreshTokenService {
    suspend fun generateRefreshToken(refreshTokenLifeTime: Int): RefreshTokenPayload
    suspend fun isValidRefreshToken(refreshTokenPayload: RefreshTokenPayload): Boolean
}
