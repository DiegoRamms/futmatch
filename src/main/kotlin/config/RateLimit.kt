package com.devapplab.config

import com.devapplab.model.auth.ClaimType
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.ratelimit.*
import java.util.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun Application.configureRateLimit() {
    install(RateLimit) {
        register(RateLimitName(RateLimitType.PUBLIC.value)) {
            rateLimiter(limit = 10, refillPeriod = 60.seconds)
            requestKey { call ->
                call.request.headers["X-Forwarded-For"]
                    ?.split(",")?.first()?.trim()
                    ?: call.request.origin.remoteHost
            }
        }

        register(RateLimitName(RateLimitType.PROTECTED.value)) {
            rateLimiter(limit = 30, refillPeriod = 60.seconds)

            requestKey { call ->
                runCatching {
                    call.getIdentifier(ClaimType.USER_IDENTIFIER)
                }.getOrNull()
                    ?: call.request.headers["X-Forwarded-For"]?.split(",")?.first()?.trim()
                    ?: call.request.origin.remoteHost
            }

            requestWeight { _, key ->
                if (key is UUID) 1 else 10
            }

        }

        register(RateLimitName(RateLimitType.REFRESH_TOKEN.value)) {
            rateLimiter(limit = 5, refillPeriod = 60.seconds)

            requestKey { call ->
                runCatching {
                    call.getIdentifier(ClaimType.USER_IDENTIFIER)
                }.getOrNull()
                    ?: call.request.headers["X-Forwarded-For"]?.split(",")?.first()?.trim()
                    ?: call.request.origin.remoteHost
            }

            requestWeight { _, key ->
                if (key is UUID) 1 else 2
            }
        }

        register(RateLimitName(RateLimitType.MFA_SEND.value)) {
            rateLimiter(limit = 30, refillPeriod = 5.minutes)
            requestKey { call ->
                call.request.headers["X-Forwarded-For"]?.split(",")?.first()?.trim()
                    ?: call.request.origin.remoteHost
            }
        }

        register(RateLimitName(RateLimitType.MFA_VERIFY.value)) {
            rateLimiter(limit = 30, refillPeriod = 5.minutes)

            requestKey { call ->
                call.request.headers["X-Forwarded-For"]?.split(",")?.first()?.trim()
                    ?: call.request.origin.remoteHost

            }
        }
    }
}

enum class RateLimitType(val value: String) {
    PROTECTED("protected"),
    PUBLIC("public"),
    REFRESH_TOKEN("refresh_token"),
    MFA_SEND("mfa_send"),
    MFA_VERIFY("mfa_verify")
}