package com.devapplab.config

import com.devapplab.model.auth.ClaimType
import com.devapplab.utils.getResetToken
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.ratelimit.*
import java.security.MessageDigest
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun Application.configureRateLimit() {
    install(RateLimit) {

        register(RateLimitName(RateLimitType.PUBLIC.value)) {
            rateLimiter(limit = 60, refillPeriod = 60.seconds)
            requestKey { call -> "public:ip:${call.clientIp()}" }
        }

        register(RateLimitName(RateLimitType.SIGN_IN.value)) {
            rateLimiter(limit = 10, refillPeriod = 5.minutes)
            requestKey { call -> "sign_in:ip:${call.clientIp()}" }
        }

        register(RateLimitName(RateLimitType.PROTECTED.value)) {
            rateLimiter(limit = 120, refillPeriod = 60.seconds)

            requestKey { call ->
                val ip = call.clientIp()
                val userId = runCatching { call.getIdentifier(ClaimType.USER_IDENTIFIER) }.getOrNull()
                if (userId != null) "protected:user:$userId:ip:$ip" else "protected:ip:$ip"
            }

            requestWeight { _, key ->
                val k = key as? String ?: return@requestWeight 50
                if (k.startsWith("protected:user:")) 1 else 50
            }
        }

        register(RateLimitName(RateLimitType.REFRESH_TOKEN.value)) {
            rateLimiter(limit = 10, refillPeriod = 60.seconds)

            requestKey { call ->
                val ip = call.clientIp()
                val userId = runCatching { call.getIdentifier(ClaimType.USER_IDENTIFIER) }.getOrNull()
                if (userId != null) "refresh:user:$userId:ip:$ip" else "refresh:ip:$ip"
            }

            requestWeight { _, key ->
                val k = key as? String ?: return@requestWeight 20
                if (k.startsWith("refresh:user:")) 1 else 20
            }
        }

        register(RateLimitName(RateLimitType.MFA_SEND.value)) {
            rateLimiter(limit = 6, refillPeriod = 5.minutes)
            requestKey { call -> "mfa-send:sign_in:ip:${call.clientIp()}" }
        }

        register(RateLimitName(RateLimitType.REST_PASSWORD_MFA_SEND.value)) {
            rateLimiter(limit = 6, refillPeriod = 5.minutes)
            requestKey { call -> "mfa-send:reset:ip:${call.clientIp()}" }
        }

        register(RateLimitName(RateLimitType.MFA_VERIFY.value)) {
            rateLimiter(limit = 15, refillPeriod = 5.minutes)
            requestKey { call -> "mfa-verify:sign_in:ip:${call.clientIp()}" }
        }

        register(RateLimitName(RateLimitType.MFA_VERIFY_REST_PASSWORD.value)) {
            rateLimiter(limit = 15, refillPeriod = 5.minutes)
            requestKey { call -> "mfa-verify:reset:ip:${call.clientIp()}" }
        }

        register(RateLimitName(RateLimitType.REST_PASSWORD_UPDATE.value)) {
            rateLimiter(limit = 10, refillPeriod = 5.minutes)

            requestKey { call ->
                val ip = call.clientIp()
                val resetToken = runCatching { call.getResetToken() }.getOrNull()
                if (resetToken != null) {
                    "password-reset:token:${sha256(resetToken)}:ip:$ip"
                } else {
                    "password-reset:ip:$ip"
                }
            }

            requestWeight { _, key ->
                val k = key as? String ?: return@requestWeight 30
                if (k.contains("token:")) 1 else 30
            }
        }

        register(RateLimitName(RateLimitType.SIGN_OUT.value)) {
            rateLimiter(limit = 30, refillPeriod = 60.seconds)

            requestKey { call ->
                val ip = call.clientIp()
                val userId = runCatching { call.getIdentifier(ClaimType.USER_IDENTIFIER) }.getOrNull()
                if (userId != null) "sign_out:user:$userId:ip:$ip" else "sign_out:ip:$ip"
            }

            requestWeight { _, key ->
                val k = key as? String ?: return@requestWeight 30
                if (k.startsWith("sign_out:user:")) 1 else 30
            }
        }
    }
}

private fun ApplicationCall.clientIp(): String =
    request.headers["X-Forwarded-For"]?.split(",")?.first()?.trim()
        ?: request.origin.remoteHost

private fun sha256(text: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

enum class RateLimitType(val value: String) {
    PROTECTED("protected"),
    SIGN_IN("sign_in"),
    SIGN_OUT("sign_out"),
    PUBLIC("public"),
    REFRESH_TOKEN("refresh_token"),
    MFA_SEND("mfa_send"),
    REST_PASSWORD_MFA_SEND("rest_password_mfa_send"),
    MFA_VERIFY("mfa_verify"),
    MFA_VERIFY_REST_PASSWORD("mfa_verify_rest_password"),
    REST_PASSWORD_UPDATE("rest_password_update"),
}