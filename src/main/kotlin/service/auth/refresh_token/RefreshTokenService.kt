package com.devapplab.service.auth.refresh_token

import com.devapplab.model.auth.RefreshTokenPayload

interface RefreshTokenService {
    fun generateRefreshToken(): RefreshTokenPayload
    fun isValidRefreshToken(refreshTokenPayload: RefreshTokenPayload): Boolean
}
