package com.devapplab.features.payment

import com.devapplab.config.RateLimitType
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.routing.*
import org.koin.ktor.plugin.scope

fun Route.paymentRouting() {
    route("payment") {
        rateLimit(RateLimitName(RateLimitType.STRIPE_WEBHOOK.value)) {
            post("webhook/stripe") {
                val controller = call.scope.get<PaymentController>()
                controller.handleStripeWebhook(call)
            }
        }
    }
}
