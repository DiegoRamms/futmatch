package com.devapplab.service.auth.auth_token

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.devapplab.config.loadECPrivateKey
import com.devapplab.model.auth.ClaimConfig
import com.devapplab.model.auth.ClaimType
import com.devapplab.model.auth.JWTConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.time.Duration.Companion.minutes

class JWTService : AuthTokenService {
    @Volatile
    private var cachedAlgorithm: Algorithm? = null

    @Volatile
    private var cachedPrivateKey: String? = null

    override suspend fun createAuthToken(claimConfig: ClaimConfig, jwtConfig: JWTConfig): String {
        return withContext(Dispatchers.Default) {
            val algorithm = getOrCreateAlgorithm(jwtConfig)
            val now = System.currentTimeMillis()

            JWT.create()
                .withIssuer(jwtConfig.issuer)
                .withAudience(jwtConfig.audience)
                .withClaim(ClaimType.USER_IDENTIFIER.value, claimConfig.userId.toString())
                .withClaim(ClaimType.USER_ROLE.value, claimConfig.userRole.toString())
                .withClaim(ClaimType.DEVICE_IDENTIFIER.value, claimConfig.deviceId.toString())
                .withExpiresAt(Date(now + jwtConfig.accessTokenLifetime.minutes.inWholeMilliseconds))
                .sign(algorithm)
        }
    }

    private fun getOrCreateAlgorithm(jwtConfig: JWTConfig): Algorithm {
        val currentPrivateKey = jwtConfig.private
        val existingAlgorithm = cachedAlgorithm
        if (existingAlgorithm != null && cachedPrivateKey == currentPrivateKey) {
            return existingAlgorithm
        }

        return synchronized(this) {
            val synchronizedAlgorithm = cachedAlgorithm
            if (synchronizedAlgorithm != null && cachedPrivateKey == currentPrivateKey) {
                synchronizedAlgorithm
            } else {
                Algorithm.ECDSA256(jwtConfig.loadECPrivateKey()).also {
                    cachedAlgorithm = it
                    cachedPrivateKey = currentPrivateKey
                }
            }
        }
    }
}
