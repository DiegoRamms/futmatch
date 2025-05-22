package com.devapplab.service.auth.auth_token

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.devapplab.config.loadECPrivateKey
import com.devapplab.model.auth.ClaimConfig
import com.devapplab.model.auth.ClaimType
import com.devapplab.model.auth.JWTConfig
import com.devapplab.utils.ACCESS_TOKEN_TIME
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class JWTService : AuthTokenService {
    override suspend fun createAuthToken(claimConfig: ClaimConfig, jwtConfig: JWTConfig): String {
        return withContext(Dispatchers.Default) {
            jwtConfig.let {
                val algorithm = Algorithm.ECDSA256(jwtConfig.loadECPrivateKey())
                val now = System.currentTimeMillis()

               val token = JWT.create()
                    .withIssuer(it.issuer)
                    .withAudience(it.audience)
                    .withClaim(ClaimType.USER_IDENTIFIER.value, claimConfig.userId.toString())
                    .withClaim(ClaimType.USER_ROLE.value, claimConfig.userRole.toString())
                    .withExpiresAt(Date(now + ACCESS_TOKEN_TIME))
                    .sign(algorithm)

                token
            }
        }
    }
}