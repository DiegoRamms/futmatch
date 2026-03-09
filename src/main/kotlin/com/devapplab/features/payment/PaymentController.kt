package com.devapplab.features.payment

import com.devapplab.config.getIdentifier
import com.devapplab.model.AppResult
import com.devapplab.model.auth.ClaimType
import com.devapplab.model.payment.CustomerSheetInitResponse
import com.devapplab.model.payment.PaymentMethodResponse
import com.devapplab.model.payment.PaymentProvider
import com.devapplab.model.payment.SetupIntentResponse
import com.devapplab.service.billing.BillingService
import com.devapplab.service.payment.PaymentService
import com.devapplab.service.payment.StripeWebhookService
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.createError
import com.devapplab.utils.retrieveLocale
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*

class PaymentController(
    private val billingService: BillingService,
    private val stripeWebhookService: StripeWebhookService,
    private val paymentService: PaymentService
) {

    suspend fun initCustomerSheet(call: ApplicationCall) {
        val userId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val provider = PaymentProvider.STRIPE

        val customerId = billingService.getOrCreateCustomer(userId, provider)
        val customerSessionSecret = billingService.createCustomerSession(customerId)

        call.respond(
            AppResult.Success(
                CustomerSheetInitResponse(
                    customerId = customerId,
                    customerSessionClientSecret = customerSessionSecret,
                    publishableKey = billingService.getPublishableKey()
                )
            )
        )
    }

    suspend fun createSetupIntent(call: ApplicationCall) {
        val userId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val provider = PaymentProvider.STRIPE

        val customerId = billingService.getOrCreateCustomer(userId, provider)
        val clientSecret = billingService.createSetupIntent(customerId)

        call.respond(
            AppResult.Success(
                SetupIntentResponse(
                    customerId = customerId,
                    clientSecret = clientSecret,
                    publishableKey = billingService.getPublishableKey()
                )
            )
        )
    }

    suspend fun listMethods(call: ApplicationCall) {
        val userId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val provider = PaymentProvider.STRIPE

        val customerId = billingService.getOrCreateCustomer(userId, provider)
        val methods = billingService.listCardPaymentMethods(customerId)
            .map {
                PaymentMethodResponse(
                    id = it.id,
                    brand = it.brand,
                    last4 = it.last4,
                    expMonth = it.expMonth,
                    expYear = it.expYear
                )
            }

        call.respond(AppResult.Success(methods))
    }

    suspend fun detachMethod(call: ApplicationCall) {
        val paymentMethodId = call.parameters["paymentMethodId"]
            ?: return call.respond(
                HttpStatusCode.BadRequest,
                AppResult.Success(false)
            )

        val ok = billingService.detachPaymentMethod(paymentMethodId)
        if (ok) {
            call.respond(HttpStatusCode.NoContent)
        } else {
            call.respond(HttpStatusCode.InternalServerError, AppResult.Success(false))
        }
    }

    suspend fun handleStripeWebhook(call: ApplicationCall) {
        val signature = call.request.header("Stripe-Signature")
        val payload = call.receiveText()

        if (signature == null) {
            call.respond(HttpStatusCode.BadRequest)
            return
        }

        val result = stripeWebhookService.handleWebhook(payload, signature)

        if (result) {
            call.respond(HttpStatusCode.OK)
        } else {
            call.respond(HttpStatusCode.BadRequest)
        }
    }

    suspend fun recoverPaymentStatus(call: ApplicationCall) {
        val userId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val matchId = call.parameters["matchId"]
        val locale = call.retrieveLocale()

        if (matchId.isNullOrBlank()) {
            val error = locale.createError(
                titleKey = StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                descriptionKey = StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY,
                status = HttpStatusCode.BadRequest
            )
            call.respond(error)
            return
        }

        val result = paymentService.recoverPaymentStatus(matchId, userId, locale)
        call.respond(result)
    }
}
