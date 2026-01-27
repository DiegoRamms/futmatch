package com.devapplab.service.password_reset

import com.devapplab.service.hashing.HashingService
import java.security.SecureRandom
import java.util.*
import kotlin.time.Duration.Companion.minutes


interface PasswordResetTokenService {
    fun generateResetTokenData(now: Long = System.currentTimeMillis()): ResetTokenData
    fun hashToken(plainToken: String): String
}

data class ResetTokenData(
    val plainToken: String,
    val hashedToken: String,
    val expiresAt: Long
)


class PasswordResetTokenServiceImpl(
    private val hashingService: HashingService
) : PasswordResetTokenService {

    private val secureRandom = SecureRandom()

    override fun generateResetTokenData(now: Long): ResetTokenData {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)

        val plainToken = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes)

        val hashedToken = hashingService.hashOpaqueToken(plainToken)
        val expiresAt = now + 10.minutes.inWholeMilliseconds

        return ResetTokenData(
            plainToken = plainToken,
            hashedToken = hashedToken,
            expiresAt = expiresAt
        )
    }

    override fun hashToken(plainToken: String): String =
        hashingService.hashOpaqueToken(plainToken)
}
