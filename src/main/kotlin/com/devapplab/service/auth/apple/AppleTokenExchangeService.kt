package com.devapplab.service.auth.apple

import com.devapplab.model.auth.AppleAuthConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/**
 * Exchanges the one-time `authorizationCode` from a register-time Apple authorization
 * for a refresh token, per https://developer.apple.com/documentation/sign_in_with_apple/generate_and_validate_tokens.
 * A failure here must never fail registration — the account is valid either way, it
 * simply won't be revocable server-side on deletion. Callers treat `null` as "log and
 * move on".
 */
class AppleTokenExchangeService(
    private val client: HttpClient,
    private val clientSecretSigner: AppleClientSecretSigner,
    private val config: AppleAuthConfig
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun exchangeAuthorizationCode(authorizationCode: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val response = client.submitForm(
                url = APPLE_TOKEN_URL,
                formParameters = Parameters.build {
                    append("client_id", config.clientId)
                    append("client_secret", clientSecretSigner.sign())
                    append("code", authorizationCode)
                    append("grant_type", "authorization_code")
                }
            )
            if (!response.status.isSuccess()) {
                logger.warn("Apple token exchange failed with status {}", response.status.value)
                return@withContext null
            }
            val body = response.body<AppleTokenExchangeResponse>()
            body.refreshToken
        }.getOrElse { error ->
            logger.warn("Apple token exchange threw: {}", error.javaClass.simpleName)
            null
        }
    }

    /**
     * Revokes a previously issued refresh token, per
     * https://developer.apple.com/documentation/sign_in_with_apple/revoke_tokens.
     * Called from account deletion — this is what App Review's 5.1.1(v) requires
     * when an app offers Sign in with Apple. Like the exchange above, a failure
     * here must not block the deletion itself; it's logged and the caller moves on.
     */
    suspend fun revoke(refreshToken: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val response = client.submitForm(
                url = APPLE_REVOKE_URL,
                formParameters = Parameters.build {
                    append("client_id", config.clientId)
                    append("client_secret", clientSecretSigner.sign())
                    append("token", refreshToken)
                    append("token_type_hint", "refresh_token")
                }
            )
            if (!response.status.isSuccess()) {
                logger.warn("Apple token revoke failed with status {}", response.status.value)
                return@withContext false
            }
            true
        }.getOrElse { error ->
            logger.warn("Apple token revoke threw: {}", error.javaClass.simpleName)
            false
        }
    }

    private companion object {
        const val APPLE_TOKEN_URL = "https://appleid.apple.com/auth/token"
        const val APPLE_REVOKE_URL = "https://appleid.apple.com/auth/revoke"
    }
}

@Serializable
private data class AppleTokenExchangeResponse(
    val access_token: String? = null,
    val refresh_token: String? = null,
    val id_token: String? = null,
    val expires_in: Long? = null,
    val token_type: String? = null
) {
    val refreshToken: String? get() = refresh_token
}
