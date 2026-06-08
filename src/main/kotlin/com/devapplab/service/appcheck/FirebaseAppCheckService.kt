package com.devapplab.service.appcheck

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.devapplab.model.AppCheckConfig
import org.slf4j.LoggerFactory
import java.net.URI
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.TimeUnit

class FirebaseAppCheckService(
    private val config: AppCheckConfig
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val jwkProvider: JwkProvider = JwkProviderBuilder(URI(JWKS_URL).toURL())
        .cached(10, 6, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .timeouts(2_000, 2_000)
        .build()

    fun verify(token: String?): AppCheckVerificationResult {
        if (!config.enabled) return AppCheckVerificationResult.Disabled

        if (config.projectNumber.isBlank()) {
            logger.warn("Firebase App Check is enabled but appCheck.projectNumber is not configured.")
            return AppCheckVerificationResult.Invalid("missing_project_number")
        }

        if (token.isNullOrBlank()) {
            return AppCheckVerificationResult.Missing
        }

        return runCatching {
            val decoded = JWT.decode(token)

            if (decoded.algorithm != "RS256") {
                return AppCheckVerificationResult.Invalid("invalid_algorithm")
            }

            if (decoded.type != "JWT") {
                return AppCheckVerificationResult.Invalid("invalid_type")
            }

            val jwk = jwkProvider.get(decoded.keyId)
            val publicKey = jwk.publicKey as? RSAPublicKey
                ?: return AppCheckVerificationResult.Invalid("invalid_public_key")

            val verifier = JWT.require(Algorithm.RSA256(publicKey, null))
                .withIssuer("https://firebaseappcheck.googleapis.com/${config.projectNumber}")
                .withAudience("projects/${config.projectNumber}")
                .build()

            val verified = verifier.verify(token)
            val appId = verified.subject

            if (appId.isNullOrBlank()) {
                return AppCheckVerificationResult.Invalid("missing_app_id")
            }

            if (config.allowedAppIds.isNotEmpty() && appId !in config.allowedAppIds) {
                return AppCheckVerificationResult.Invalid("app_id_not_allowed")
            }

            AppCheckVerificationResult.Valid(appId)
        }.getOrElse { error ->
            AppCheckVerificationResult.Invalid(error.javaClass.simpleName)
        }
    }

    companion object {
        private const val JWKS_URL = "https://firebaseappcheck.googleapis.com/v1/jwks"
    }
}

sealed interface AppCheckVerificationResult {
    data object Disabled : AppCheckVerificationResult
    data object Missing : AppCheckVerificationResult
    data class Valid(val appId: String) : AppCheckVerificationResult
    data class Invalid(val reason: String) : AppCheckVerificationResult
}
