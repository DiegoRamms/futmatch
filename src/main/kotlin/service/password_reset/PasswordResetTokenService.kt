package com.devapplab.service.password_reset

import com.devapplab.data.repository.password_reset.PasswordResetTokenRepository
import com.devapplab.service.hashing.HashingService
import java.security.SecureRandom
import java.util.*
import kotlin.time.Duration.Companion.minutes

interface PasswordResetTokenService {
    suspend fun createAndSaveResetToken(userId: UUID): String
    suspend fun verifyResetToken(token: String, locale: Locale): Boolean
    suspend fun invalidateToken(token: String)
}

class PasswordResetTokenServiceImpl(
    private val repository: PasswordResetTokenRepository,
    private val hashingService: HashingService
) : PasswordResetTokenService {

    private val secureRandom = SecureRandom()

    override suspend fun createAndSaveResetToken(userId: UUID): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        val plainToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        val hashedToken = hashingService.hashOpaqueToken(plainToken)
        val expiresAt = System.currentTimeMillis() + 10.minutes.inWholeMilliseconds// 10 minutes

        repository.create(hashedToken, userId, expiresAt)
        return plainToken
    }

    override suspend fun verifyResetToken(token: String, locale: Locale): Boolean {
        val hashedInputToken = hashingService.hashOpaqueToken(token)
        val record = repository.findByToken(hashedInputToken)

        return when {
            record == null -> false
            record.expiresAt < System.currentTimeMillis() -> {
                repository.delete(hashedInputToken) // Delete expired token
                false
            }
            else -> true
        }
    }

    override suspend fun invalidateToken(token: String) {
        val hashedInputToken = hashingService.hashOpaqueToken(token)
        repository.delete(hashedInputToken)
    }
}