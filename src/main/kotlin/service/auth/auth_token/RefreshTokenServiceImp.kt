package com.devapplab.service.auth.auth_token

import com.devapplab.data.repository.RefreshTokenRepository
import com.devapplab.model.auth.RefreshTokenPayload
import com.devapplab.service.auth.refresh_token.RefreshTokenService
import com.devapplab.service.hashing.HashingService
import com.devapplab.utils.ONE_WEEK_IN_MILLIS
import java.security.SecureRandom
import java.util.*

class RefreshTokenServiceImp(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val hashingService: HashingService
) : RefreshTokenService {

    override fun generateRefreshToken(): RefreshTokenPayload {
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val hashedToken = hashingService.hash(token)
        val expiresAt = System.currentTimeMillis() + ONE_WEEK_IN_MILLIS
        return RefreshTokenPayload(plainToken = token, hashedToken = hashedToken, expiresAt = expiresAt)
    }

    override suspend fun saveRefreshToken(userId: UUID, deviceId: UUID, refreshTokenPayload: RefreshTokenPayload) {
        refreshTokenRepository.saveToken(
            userId = userId,
            deviceId = deviceId,
            token = refreshTokenPayload.hashedToken,
            expiresAt = refreshTokenPayload.expiresAt
        )
    }

    override suspend fun revokeRefreshToken(deviceId: UUID): Boolean {
        return refreshTokenRepository.revokeToken(deviceId)
    }

    override fun isValidRefreshToken(refreshTokenPayload: RefreshTokenPayload): Boolean {
        println(refreshTokenPayload)
        val isValidToken = hashingService.verify(refreshTokenPayload.plainToken, refreshTokenPayload.hashedToken)
        val isNotExpired = System.currentTimeMillis() < refreshTokenPayload.expiresAt

        return isValidToken && isNotExpired
    }
}
