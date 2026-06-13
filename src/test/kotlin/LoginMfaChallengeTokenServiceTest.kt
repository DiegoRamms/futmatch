package com.devapplab

import com.devapplab.service.auth.mfa.LoginMfaChallengeTokenServiceImpl
import com.devapplab.service.hashing.HashingServiceImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoginMfaChallengeTokenServiceTest {

    private val service = LoginMfaChallengeTokenServiceImpl(HashingServiceImpl())

    @Test
    fun `generate challenge token data creates opaque token with five minute expiry`() {
        val now = 1_000L

        val tokenData = service.generateChallengeTokenData(now)

        assertTrue(tokenData.plainToken.isNotBlank())
        assertEquals(service.hashToken(tokenData.plainToken), tokenData.hashedToken)
        assertEquals(now + 5 * 60 * 1000, tokenData.expiresAt)
    }
}
