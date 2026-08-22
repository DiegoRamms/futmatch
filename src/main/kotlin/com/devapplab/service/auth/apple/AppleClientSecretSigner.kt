package com.devapplab.service.auth.apple

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.devapplab.model.auth.AppleAuthConfig
import java.security.KeyFactory
import java.security.interfaces.ECPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.Date

/**
 * Builds the ES256 `client_secret` JWT Apple's token endpoint requires in place of a
 * static secret. Signed with the `.p8` private key from the Apple Developer portal;
 * `iss`/`kid` identify the team and key, `sub` is the bundle id (the app's `client_id`
 * in the native flow), `aud` is fixed to Apple's issuer.
 */
class AppleClientSecretSigner(private val config: AppleAuthConfig) {
    private val privateKey: ECPrivateKey by lazy { parsePrivateKey(config.privateKeyBase64) }

    fun sign(): String {
        val now = Date()
        val expiresAt = Date(now.time + CLIENT_SECRET_LIFETIME_MS)
        return JWT.create()
            .withKeyId(config.keyId)
            .withIssuer(config.teamId)
            .withIssuedAt(now)
            .withExpiresAt(expiresAt)
            .withAudience(APPLE_TOKEN_AUDIENCE)
            .withSubject(config.clientId)
            .sign(Algorithm.ECDSA256(null, privateKey))
    }

    private fun parsePrivateKey(privateKeyBase64: String): ECPrivateKey {
        val pemText = String(Base64.getDecoder().decode(privateKeyBase64), Charsets.UTF_8)
        val der = Base64.getDecoder().decode(
            pemText
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\s".toRegex(), "")
        )
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePrivate(PKCS8EncodedKeySpec(der)) as ECPrivateKey
    }

    private companion object {
        const val APPLE_TOKEN_AUDIENCE = "https://appleid.apple.com"
        // Apple caps this at 6 months; a fresh one is minted per exchange, so there is
        // no need to push the limit.
        const val CLIENT_SECRET_LIFETIME_MS = 5 * 60 * 1000L
    }
}
