package com.devapplab.features.payment

import com.devapplab.service.payment.PaymentServiceFactory
import com.devapplab.service.payment.StripeWebhookService
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable

class PaymentController(
    private val stripeWebhookService: StripeWebhookService
) {

    suspend fun handleStripeWebhook(call: ApplicationCall) {
        val signature = call.request.header("Stripe-Signature")
        val payload = call.receiveText()

        if (signature == null) {
            call.respond(io.ktor.http.HttpStatusCode.BadRequest)
            return
        }

        val result = stripeWebhookService.handleWebhook(payload, signature)

        if (result) {
            call.respond(io.ktor.http.HttpStatusCode.OK)
        } else {
            call.respond(io.ktor.http.HttpStatusCode.BadRequest)
        }
    }
}
