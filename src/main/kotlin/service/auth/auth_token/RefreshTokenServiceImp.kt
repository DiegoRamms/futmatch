package com.devapplab.service.auth.auth_token

import com.devapplab.model.auth.RefreshTokenPayload
import com.devapplab.service.auth.refresh_token.RefreshTokenService
import com.devapplab.service.hashing.HashingService
import java.security.SecureRandom
import java.util.*
import kotlin.time.Duration.Companion.days

class RefreshTokenServiceImp(
    private val hashingService: HashingService
) : RefreshTokenService {

    override suspend fun generateRefreshToken(refreshTokenLifeTime: Int): RefreshTokenPayload {
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val hashedToken = hashingService.hashOpaqueToken(token)
        val expiresAt = System.currentTimeMillis() + refreshTokenLifeTime.days.inWholeMilliseconds
        return RefreshTokenPayload(plainToken = token, hashedToken = hashedToken, expiresAt = expiresAt)
    }

    override suspend fun isValidRefreshToken(refreshTokenPayload: RefreshTokenPayload): Boolean {
        val hashedInput = hashingService.hashOpaqueToken(refreshTokenPayload.plainToken)

        val matches = hashedInput == refreshTokenPayload.hashedToken
        val isNotExpired = System.currentTimeMillis() < refreshTokenPayload.expiresAt

        return matches && isNotExpired
    }
}
