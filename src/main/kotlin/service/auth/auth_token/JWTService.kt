package com.devapplab.service.auth.auth_token

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.devapplab.config.loadECPrivateKey
import com.devapplab.model.auth.ClaimConfig
import com.devapplab.model.auth.ClaimType
import com.devapplab.model.auth.JWTConfig
import java.util.*

class JWTService : AuthTokenService {
    override fun createAuthToken(claimConfig: ClaimConfig, jwtConfig: JWTConfig): String {
        jwtConfig.apply {
            val algorithm = Algorithm.ECDSA256(jwtConfig.loadECPrivateKey())
            val now = System.currentTimeMillis()
            val oneDayInMillis = 1L * 24L * 60L * 60L * 1000L

            val token = JWT.create()
                .withIssuer(issuer)
                .withAudience(audience)
                .withClaim(ClaimType.USER_IDENTIFIER.value, claimConfig.userId.toString())
                .withClaim(ClaimType.IS_EMAIL_VERIFIED.value, claimConfig.isEmailVerified)
                .withExpiresAt(Date(now + oneDayInMillis))
                .sign(algorithm)
            return token
        }
    }
}