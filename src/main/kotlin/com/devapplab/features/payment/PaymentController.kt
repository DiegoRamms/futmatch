package com.devapplab.features.payment

import com.devapplab.config.getIdentifier
import com.devapplab.model.AppResult
import com.devapplab.model.auth.ClaimType
import com.devapplab.model.payment.CustomerSheetInitResponse
import com.devapplab.model.payment.PaymentMethodResponse
import com.devapplab.model.payment.PaymentProvider
import com.devapplab.model.payment.SetupIntentResponse
import com.devapplab.observability.appFailure
import com.devapplab.observability.appRejected
import com.devapplab.observability.appSuccess
import com.devapplab.observability.requestContext
import com.devapplab.service.billing.BillingService
import com.devapplab.service.payment.PaymentService
import com.devapplab.service.payment.PendingMatchPaymentService
import com.devapplab.service.payment.StripeWebhookService
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.createError
import com.devapplab.utils.respond
import com.devapplab.utils.retrieveLocale
import com.devapplab.utils.toUUIDOrNull
import com.stripe.exception.StripeException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import org.slf4j.LoggerFactory

class PaymentController(
    private val billingService: BillingService,
    private val stripeWebhookService: StripeWebhookService,
    private val paymentService: PaymentService,
    private val pendingMatchPaymentService: PendingMatchPaymentService
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun initCustomerSheet(call: ApplicationCall) {
        val locale = call.retrieveLocale()
        val context = call.requestContext()

        try {
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
            logger.appSuccess(
                event = "payment.customer_sheet.initialized",
                context = context,
                message = "Customer sheet initialized",
                userId = userId,
                statusCode = HttpStatusCode.OK.value,
                extra = mapOf("provider" to provider.name)
            )
        } catch (e: StripeException) {
            logger.error(
                "Stripe error initializing customer sheet. statusCode={}, requestId={}, code={}, param={}, message={}",
                e.statusCode,
                e.requestId,
                e.stripeError?.code,
                e.stripeError?.param,
                e.stripeError?.message,
                e
            )
            logger.appFailure(
                event = "payment.customer_sheet.init_failed",
                context = context,
                message = "Customer sheet initialization failed in Stripe",
                reason = "stripe_error",
                statusCode = HttpStatusCode.InternalServerError.value,
                throwable = e
            )
            call.respond(
                locale.createError(
                    titleKey = StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                    descriptionKey = StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY,
                    status = HttpStatusCode.InternalServerError
                )
            )
        } catch (e: Exception) {
            logger.error("Unexpected error initializing customer sheet", e)
            logger.appFailure(
                event = "payment.customer_sheet.init_failed",
                context = context,
                message = "Customer sheet initialization failed unexpectedly",
                reason = "unexpected_error",
                statusCode = HttpStatusCode.InternalServerError.value,
                throwable = e
            )
            call.respond(
                locale.createError(
                    titleKey = StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                    descriptionKey = StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY,
                    status = HttpStatusCode.InternalServerError
                )
            )
        }
    }

    suspend fun createSetupIntent(call: ApplicationCall) {
        val context = call.requestContext()
        val locale = call.retrieveLocale()
        try {
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
            logger.appSuccess(
                event = "payment.setup_intent.created",
                context = context,
                message = "Setup intent created",
                userId = userId,
                statusCode = HttpStatusCode.OK.value,
                extra = mapOf("provider" to provider.name)
            )
        } catch (e: Exception) {
            logger.appFailure(
                event = "payment.setup_intent.create_failed",
                context = context,
                message = "Setup intent creation failed",
                reason = "unexpected_error",
                statusCode = HttpStatusCode.InternalServerError.value,
                throwable = e
            )
            call.respond(
                locale.createError(
                    titleKey = StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                    descriptionKey = StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY,
                    status = HttpStatusCode.InternalServerError
                )
            )
        }
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
        val context = call.requestContext()
        val userId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val paymentMethodId = call.parameters["paymentMethodId"]
            ?: run {
                logger.appRejected(
                    event = "payment.method.detach_failed",
                    context = context,
                    message = "Payment method detach rejected because the payment method id is missing",
                    reason = "missing_payment_method_id",
                    userId = userId,
                    statusCode = HttpStatusCode.BadRequest.value
                )
                return call.respond(HttpStatusCode.BadRequest, AppResult.Success(false))
            }

        val ok = billingService.detachPaymentMethod(paymentMethodId)
        if (ok) {
            logger.appSuccess(
                event = "payment.method.detached",
                context = context,
                message = "Payment method detached",
                userId = userId,
                statusCode = HttpStatusCode.NoContent.value
            )
            call.respond(HttpStatusCode.NoContent)
        } else {
            logger.appFailure(
                event = "payment.method.detach_failed",
                context = context,
                message = "Payment method detach failed",
                reason = "detach_failed",
                userId = userId,
                statusCode = HttpStatusCode.InternalServerError.value
            )
            call.respond(HttpStatusCode.InternalServerError, AppResult.Success(false))
        }
    }

    suspend fun getPendingMatchPayment(call: ApplicationCall) {
        val matchId = call.parameters["matchId"]?.toUUIDOrNull()
            ?: throw NotFoundException("Can't recover pending match payment")
        val userId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val locale = call.retrieveLocale()
        val result = pendingMatchPaymentService.getPendingPayment(matchId, userId, locale, call.requestContext())
        call.respond(result)
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
        val context = call.requestContext()

        if (matchId.isNullOrBlank()) {
            logger.appRejected(
                event = "payment.status.recover_failed",
                context = context,
                message = "Payment status recovery rejected because the match id is missing",
                reason = "missing_match_id",
                userId = userId,
                statusCode = HttpStatusCode.BadRequest.value
            )
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

    suspend fun validatePayment(call: ApplicationCall) {
        val providerPaymentId = call.parameters["providerPaymentId"]
        val locale = call.retrieveLocale()
        val context = call.requestContext()

        if (providerPaymentId.isNullOrBlank()) {
            logger.appRejected(
                event = "payment.status.validate_failed",
                context = context,
                message = "Payment validation rejected because the provider payment id is missing",
                reason = "missing_provider_payment_id",
                statusCode = HttpStatusCode.BadRequest.value
            )
            val error = locale.createError(
                titleKey = StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                descriptionKey = StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY,
                status = HttpStatusCode.BadRequest
            )
            call.respond(error)
            return
        }

        val result = paymentService.validatePaymentStatus(providerPaymentId, locale)
        call.respond(result)
    }

    suspend fun pollPaymentStatus(call: ApplicationCall) {
        val userId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val matchId = call.parameters["matchId"]
        val locale = call.retrieveLocale()
        val context = call.requestContext()

        if (matchId.isNullOrBlank()) {
            logger.appRejected(
                event = "payment.status.poll_failed",
                context = context,
                message = "Payment status poll rejected because the match id is missing",
                reason = "missing_match_id",
                userId = userId,
                statusCode = HttpStatusCode.BadRequest.value
            )
            val error = locale.createError(
                titleKey = StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                descriptionKey = StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY,
                status = HttpStatusCode.BadRequest
            )
            call.respond(error)
            return
        }

        val result = paymentService.getPollingStatus(matchId, userId, locale)
        call.respond(result)
    }
}
