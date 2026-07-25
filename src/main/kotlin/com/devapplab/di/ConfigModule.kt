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
import org.slf4j.LoggerFactory

private val configLogger = LoggerFactory.getLogger("AppCheckConfig")

val configModule = module {
    single {
        val config = get<ApplicationConfig>()
        val enabled = config.propertyOrNull("appCheck.enabled")?.getString()?.toBooleanStrictOrNull() ?: true
        val enforce = config.propertyOrNull("appCheck.enforce")?.getString()?.toBooleanStrictOrNull() ?: true
        val projectNumber = config.propertyOrNull("appCheck.projectNumber")?.getString().orEmpty()

        if (enforce && !enabled) {
            val message = "Invalid App Check configuration: appCheck.enforce requires appCheck.enabled=true."
            configLogger.error(message)
            throw IllegalStateException(message)
        }
        if (enabled && projectNumber.isBlank()) {
            val message = "Invalid App Check configuration: FIREBASE_PROJECT_NUMBER is required when App Check is enabled."
            configLogger.error(message)
            throw IllegalStateException(message)
        }

        AppCheckConfig(
            enabled = enabled,
            enforce = enforce,
            projectNumber = projectNumber,
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
