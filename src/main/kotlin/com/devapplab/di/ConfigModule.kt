package com.devapplab.di

import com.devapplab.model.EmailConfig
import com.devapplab.model.EmailDomainPolicyConfig
import com.devapplab.model.AppCheckConfig
import com.devapplab.model.MatchPaymentConfig
import com.devapplab.model.StripeConfig
import com.devapplab.model.WebhookConfig
import com.devapplab.service.auth.mfa.MfaRateLimitConfig
import com.devapplab.utils.loadDomainResource
import com.devapplab.utils.toDomainSet
import io.ktor.server.config.ApplicationConfig
import org.koin.dsl.module

val configModule = module {
    single {
        val config = get<ApplicationConfig>()
        AppCheckConfig(
            enabled = config.propertyOrNull("appCheck.enabled")?.getString()?.toBooleanStrictOrNull() ?: false,
            enforce = config.propertyOrNull("appCheck.enforce")?.getString()?.toBooleanStrictOrNull() ?: false,
            projectNumber = config.propertyOrNull("appCheck.projectNumber")?.getString().orEmpty(),
            allowedAppIds = config.propertyOrNull("appCheck.allowedAppIds")
                ?.getString()
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                ?: emptySet()
        )
    }

    single {
        val config = get<ApplicationConfig>()
        MatchPaymentConfig(
            maxJoinPaymentWindowHours = config.propertyOrNull("match.payment.maxJoinWindowHours")
                ?.getString()
                ?.toLongOrNull()
                ?: 120
        )
    }

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
            fromEmail = "hello@futmatch.mx",
            fromName = "Futmatch"
        )
    }
    single {
        val config = get<ApplicationConfig>()
        EmailDomainPolicyConfig(
            enforceAllowlist = config.propertyOrNull("email.domainPolicy.enforceAllowlist")
                ?.getString()
                ?.toBooleanStrictOrNull()
                ?: false,
            allowedDomains = loadDomainResource("email_allowed_domains.txt") +
                config.propertyOrNull("email.domainPolicy.allowedDomains")
                    ?.getString()
                    .toDomainSet(),
            blockedDomains = loadDomainResource("email_blocked_domains.txt") +
                config.propertyOrNull("email.domainPolicy.blockedDomains")
                    ?.getString()
                    .toDomainSet()
        )
    }
    single {
        val config = get<ApplicationConfig>()
        StripeConfig(
            apiKey = config.propertyOrNull("payment.stripe.apiKey")?.getString() ?: "",
            publishableKey = config.propertyOrNull("payment.stripe.publishableKey")?.getString() ?: ""
        )
    }

    single {
        val config = get<ApplicationConfig>()
        WebhookConfig(
            webhookSecret = config.propertyOrNull("payment.stripe.webhookSecret")?.getString() ?: "",
        )
    }
}
