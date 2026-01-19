package com.devapplab.di

import com.devapplab.model.EmailConfig
import com.devapplab.service.auth.mfa.MfaRateLimitConfig
import io.ktor.server.config.ApplicationConfig
import org.koin.dsl.module

val configModule = module {
    single {
        val config = get<ApplicationConfig>()
        MfaRateLimitConfig(
            minWaitSeconds = config.property("mfa.rateLimit.minWaitSeconds").getString().toLong(),
            maxAttempts = config.property("mfa.lockout.maxAttempts").getString().toInt(),
            timeWindowHours = config.property("mfa.lockout.timeWindowHours").getString().toLong(),
            lockDurationMinutes = config.property("mfa.lockout.lockDurationMinutes").getString().toInt()
        )
    }
    single {
        val config = get<ApplicationConfig>()
        EmailConfig(
            apiToken = config.property("email.apiToken").getString(),
            fromEmail = "hello@demomailtrap.co",
            fromName = "Futmatch Test"
        )
    }
}
