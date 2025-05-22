package com.devapplab.service.auth.auth_token

import com.devapplab.model.auth.ClaimConfig
import com.devapplab.model.auth.JWTConfig

interface AuthTokenService {
    suspend fun createAuthToken(claimConfig: ClaimConfig, jwtConfig: JWTConfig): String
}