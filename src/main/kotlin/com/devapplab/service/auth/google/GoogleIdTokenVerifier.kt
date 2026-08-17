package com.devapplab.service.auth.google

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.devapplab.model.auth.GoogleAuthConfig
import org.slf4j.LoggerFactory
import java.net.URI
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.TimeUnit

class GoogleIdTokenVerifier(
    private val config: GoogleAuthConfig
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val jwkProvider: JwkProvider = JwkProviderBuilder(URI(GOOGLE_JWKS_URL).toURL())
        .cached(10, 6, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .timeouts(2_000, 2_000)
        .build()

    fun verify(idToken: String): GoogleIdTokenVerificationResult {
        if (idToken.isBlank()) {
            return GoogleIdTokenVerificationResult.Invalid("missing_token")
        }

        return runCatching {
            val decoded = JWT.decode(idToken)
            if (decoded.algorithm != "RS256" || decoded.type != "JWT") {
                return GoogleIdTokenVerificationResult.Invalid("invalid_token_format")
            }

            val issuer = decoded.issuer
            if (issuer !in GOOGLE_ISSUERS) {
                return GoogleIdTokenVerificationResult.Invalid("invalid_issuer")
            }

            val publicKey = jwkProvider.get(decoded.keyId).publicKey as? RSAPublicKey
                ?: return GoogleIdTokenVerificationResult.Invalid("invalid_public_key")

            val verified = JWT.require(Algorithm.RSA256(publicKey, null))
                .withIssuer(issuer)
                .withAudience(config.webClientId)
                .build()
                .verify(idToken)

            val subject = verified.subject?.trim().orEmpty()
            val email = verified.getClaim("email").asString()?.trim().orEmpty()
            val emailVerified = verified.getClaim("email_verified").asBoolean() == true
            if (subject.isBlank()) {
                return GoogleIdTokenVerificationResult.Invalid("missing_subject")
            }
            if (email.isBlank() || !emailVerified) {
                return GoogleIdTokenVerificationResult.Invalid("unverified_email")
            }

            GoogleIdTokenVerificationResult.Valid(
                GoogleVerifiedIdentity(
                    issuer = issuer,
                    subject = subject,
                    email = email,
                    displayName = verified.getClaim("name").asString()?.trim()?.ifBlank { null },
                    givenName = verified.getClaim("given_name").asString()?.trim()?.ifBlank { null },
                    familyName = verified.getClaim("family_name").asString()?.trim()?.ifBlank { null },
                    profilePictureUrl = verified.getClaim("picture").asString()?.trim()?.ifBlank { null }
                )
            )
        }.getOrElse { error ->
            logger.warn("Google ID token verification failed: {}", error.javaClass.simpleName)
            GoogleIdTokenVerificationResult.Invalid("invalid_token")
        }
    }

    private companion object {
        const val GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs"
        val GOOGLE_ISSUERS = setOf("accounts.google.com", "https://accounts.google.com")
    }
}

data class GoogleVerifiedIdentity(
    val issuer: String,
    val subject: String,
    val email: String,
    val displayName: String?,
    val givenName: String?,
    val familyName: String?,
    val profilePictureUrl: String?
)

sealed interface GoogleIdTokenVerificationResult {
    data class Valid(val identity: GoogleVerifiedIdentity) : GoogleIdTokenVerificationResult
    data class Invalid(val reason: String) : GoogleIdTokenVerificationResult
}
