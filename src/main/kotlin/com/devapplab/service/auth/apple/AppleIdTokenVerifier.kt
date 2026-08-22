package com.devapplab.service.auth.apple

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.devapplab.model.auth.AppleAuthConfig
import org.slf4j.LoggerFactory
import java.net.URI
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.TimeUnit

class AppleIdTokenVerifier(
    private val config: AppleAuthConfig
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val jwkProvider: JwkProvider = JwkProviderBuilder(URI(APPLE_JWKS_URL).toURL())
        .cached(10, 6, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .timeouts(2_000, 2_000)
        .build()

    /**
     * `nonce` is the raw value the client generated and sent in the request body; its SHA-256
     * hex digest must equal the token's `nonce` claim. Apple's SDK has no built-in replay
     * defence the way Google's does, so this check is the client<->request binding.
     */
    fun verify(idToken: String, nonce: String): AppleIdTokenVerificationResult {
        if (idToken.isBlank()) {
            return AppleIdTokenVerificationResult.Invalid("missing_token")
        }
        if (nonce.isBlank()) {
            return AppleIdTokenVerificationResult.Invalid("missing_nonce")
        }

        return runCatching {
            val decoded = JWT.decode(idToken)
            if (decoded.algorithm != "RS256" || decoded.type != "JWT") {
                return AppleIdTokenVerificationResult.Invalid("invalid_token_format")
            }

            val issuer = decoded.issuer
            if (issuer != APPLE_ISSUER) {
                return AppleIdTokenVerificationResult.Invalid("invalid_issuer")
            }

            val publicKey = jwkProvider.get(decoded.keyId).publicKey as? RSAPublicKey
                ?: return AppleIdTokenVerificationResult.Invalid("invalid_public_key")

            val verified = JWT.require(Algorithm.RSA256(publicKey, null))
                .withIssuer(issuer)
                .withAudience(config.clientId)
                .build()
                .verify(idToken)

            val claimedNonce = verified.getClaim("nonce").asString()?.trim().orEmpty()
            if (claimedNonce.isBlank() || claimedNonce != sha256Hex(nonce)) {
                return AppleIdTokenVerificationResult.Invalid("nonce_mismatch")
            }

            val subject = verified.subject?.trim().orEmpty()
            if (subject.isBlank()) {
                return AppleIdTokenVerificationResult.Invalid("missing_subject")
            }

            // Apple omits `email` entirely on a repeat authorization, and sends
            // `email_verified` as either a boolean or a string depending on client/version.
            val email = verified.getClaim("email").asString()?.trim()?.ifBlank { null }
            val emailVerifiedClaim = verified.getClaim("email_verified")
            val emailVerified = emailVerifiedClaim.asBoolean() ?: emailVerifiedClaim.asString()?.toBooleanStrictOrNull() ?: false
            val isPrivateEmail = verified.getClaim("is_private_email").let { claim ->
                claim.asBoolean() ?: claim.asString()?.toBooleanStrictOrNull() ?: false
            }

            AppleIdTokenVerificationResult.Valid(
                AppleVerifiedIdentity(
                    issuer = issuer,
                    subject = subject,
                    email = email?.takeIf { emailVerified },
                    isPrivateEmail = isPrivateEmail
                )
            )
        }.getOrElse { error ->
            logger.warn("Apple ID token verification failed: {}", error.javaClass.simpleName)
            AppleIdTokenVerificationResult.Invalid("invalid_token")
        }
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val APPLE_JWKS_URL = "https://appleid.apple.com/auth/keys"
        const val APPLE_ISSUER = "https://appleid.apple.com"
    }
}

data class AppleVerifiedIdentity(
    val issuer: String,
    val subject: String,
    /** `null` when Apple omitted the claim (repeat authorization) or it wasn't verified. */
    val email: String?,
    val isPrivateEmail: Boolean
)

sealed interface AppleIdTokenVerificationResult {
    data class Valid(val identity: AppleVerifiedIdentity) : AppleIdTokenVerificationResult
    data class Invalid(val reason: String) : AppleIdTokenVerificationResult
}
