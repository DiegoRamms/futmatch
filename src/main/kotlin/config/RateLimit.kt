package com.devapplab.config

import com.devapplab.model.auth.ClaimType
import com.devapplab.model.auth.request.ForgotPasswordRequest
import com.devapplab.model.auth.request.SignInRequest
import com.devapplab.utils.getResetToken
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.ratelimit.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import model.mfa.VerifyResetMfaRequest
import io.ktor.server.request.*
import model.mfa.MfaCodeRequest
import model.mfa.MfaCodeVerificationRequest
import java.security.MessageDigest

fun Application.configureRateLimit() {
    install(RateLimit) {
        register(RateLimitName(RateLimitType.PUBLIC.value)) {
            rateLimiter(limit = 10, refillPeriod = 60.seconds)
            requestKey { call ->
                call.clientIp()
            }
        }

        register(RateLimitName(RateLimitType.SIGN_IN.value)) {
            rateLimiter(limit = 10, refillPeriod = 5.minutes)

            requestKey { call ->
                val ip = call.clientIp()
                val req = runCatching { call.receive<SignInRequest>() }.getOrNull()
                val email = req?.email?.trim()?.lowercase().orEmpty()

                if (email.isBlank()) return@requestKey "login:ip:$ip"

                val emailHash = sha256(email)
                "login:email:$emailHash:ip:$ip"
            }
        }

        register(RateLimitName(RateLimitType.PROTECTED.value)) {
            rateLimiter(limit = 30, refillPeriod = 60.seconds)

            requestKey { call ->
                val ip = call.clientIp()

                val userId = runCatching {
                    call.getIdentifier(ClaimType.USER_IDENTIFIER) // UUID
                }.getOrNull()

                if (userId != null) {
                    "protected:user:$userId:ip:$ip"
                } else {
                    "protected:ip:$ip"
                }
            }

            requestWeight { _, key ->
                val k = key as? String ?: return@requestWeight 20
                if (k.startsWith("protected:user:")) 1 else 20
            }

        }

        register(RateLimitName(RateLimitType.REFRESH_TOKEN.value)) {
            rateLimiter(limit = 5, refillPeriod = 60.seconds)

            requestKey { call ->
                val ip = call.clientIp()

                val userId = runCatching {
                    call.getIdentifier(ClaimType.USER_IDENTIFIER)
                }.getOrNull()

                if (userId != null) {
                    "refresh:user:$userId:ip:$ip"
                } else {
                    "refresh:ip:$ip"
                }
            }

            requestWeight { _, key ->
                val k = key as? String ?: return@requestWeight 10
                if (k.startsWith("refresh:user:")) 1 else 10
            }
        }

        register(RateLimitName(RateLimitType.MFA_SEND.value)) {
            rateLimiter(limit = 5, refillPeriod = 5.minutes)

            requestKey { call ->
                val ip = call.clientIp()

                val userId = runCatching {
                    call.receive<MfaCodeRequest>().userId
                }.getOrNull()

                if (userId != null) {
                    "mfa-send:login:user:$userId:ip:$ip"
                } else {
                    "mfa-send:login:ip:$ip"
                }
            }
        }


        register(RateLimitName(RateLimitType.REST_PASSWORD_MFA_SEND.value)) {
            rateLimiter(limit = 5, refillPeriod = 5.minutes)

            requestKey { call ->
                val ip = call.clientIp()

                val req = runCatching { call.receive<ForgotPasswordRequest>() }.getOrNull()

                val email = req?.email
                    ?.trim()
                    ?.lowercase()
                    .orEmpty()

                if (email.isBlank()) return@requestKey "reset:$ip"

                val emailHash = sha256(email)

                "mfa-send:reset:$emailHash:$ip"
            }
        }

        register(RateLimitName(RateLimitType.MFA_VERIFY.value)) {
            rateLimiter(limit = 5, refillPeriod = 5.minutes)

            requestKey { call ->
                val ip = call.clientIp()

                val userId = runCatching {
                    call.receive<MfaCodeVerificationRequest>().userId
                }.getOrNull()

                if (userId != null) {
                    "mfa-verify:login:user:$userId:ip:$ip"
                } else {
                    "mfa-verify:login:ip:$ip"
                }
            }
        }

        register(RateLimitName(RateLimitType.MFA_VERIFY_REST_PASSWORD.value)) {
            rateLimiter(limit = 5, refillPeriod = 5.minutes)

            requestKey { call ->
                val ip = call.clientIp()

                val userId = runCatching {
                    call.receive<VerifyResetMfaRequest>().userId
                }.getOrNull()

                if (userId != null) {
                    "mfa-verify:reset:user:$userId:ip:$ip"
                } else {
                    "mfa-verify:reset:ip:$ip"
                }
            }
        }

        register(RateLimitName(RateLimitType.REST_PASSWORD_UPDATE.value)) {
            rateLimiter(limit = 5, refillPeriod = 5.minutes)

            requestKey { call ->
                val ip = call.clientIp()

                val resetToken = runCatching {
                    call.getResetToken()
                }.getOrNull()

                if (resetToken != null) {
                    "password-reset:token:${sha256(resetToken)}:ip:$ip"
                } else {
                    // sin token → sospechoso
                    "password-reset:ip:$ip"
                }
            }
            requestWeight { _, key ->
                val k = key as? String ?: return@requestWeight 20
                if (k.contains("token:")) 1 else 20
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
    PUBLIC("public"),
    REFRESH_TOKEN("refresh_token"),
    MFA_SEND("mfa_send"),
    REST_PASSWORD_MFA_SEND("rest_password_mfa_send"),
    MFA_VERIFY("mfa_verify"),
    MFA_VERIFY_REST_PASSWORD("mfa_verify_rest_password"),
    REST_PASSWORD_UPDATE("rest_password_update"),
}