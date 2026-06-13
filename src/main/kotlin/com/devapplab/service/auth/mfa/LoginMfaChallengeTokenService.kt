package com.devapplab.service.auth.mfa

import com.devapplab.service.hashing.HashingService
import java.security.SecureRandom
import java.util.Base64
import kotlin.time.Duration.Companion.minutes

interface LoginMfaChallengeTokenService {
    fun generateChallengeTokenData(now: Long = System.currentTimeMillis()): LoginMfaChallengeTokenData
    fun hashToken(plainToken: String): String
}

data class LoginMfaChallengeTokenData(
    val plainToken: String,
    val hashedToken: String,
    val expiresAt: Long
)

class LoginMfaChallengeTokenServiceImpl(
    private val hashingService: HashingService
) : LoginMfaChallengeTokenService {

    private val secureRandom = SecureRandom()

    override fun generateChallengeTokenData(now: Long): LoginMfaChallengeTokenData {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)

        val plainToken = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes)

        return LoginMfaChallengeTokenData(
            plainToken = plainToken,
            hashedToken = hashingService.hashOpaqueToken(plainToken),
            expiresAt = now + 5.minutes.inWholeMilliseconds
        )
    }

    override fun hashToken(plainToken: String): String =
        hashingService.hashOpaqueToken(plainToken)
}
