package com.devapplab.features.payment

import com.devapplab.config.RateLimitType
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.plugin.scope


fun Route.stripeRouting() {
    route("payment") {
        rateLimit(RateLimitName(RateLimitType.STRIPE_WEBHOOK.value)) {
            post("webhook/stripe") {
                val controller = call.scope.get<PaymentController>()
                controller.handleStripeWebhook(call)
            }
        }
    }
}