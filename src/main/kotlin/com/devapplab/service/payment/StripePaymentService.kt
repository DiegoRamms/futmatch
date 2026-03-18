package com.devapplab.service.payment

import com.devapplab.data.repository.match.MatchRepository
import com.devapplab.data.repository.payment.PaymentRepository
import com.devapplab.model.AppResult
import com.devapplab.model.ErrorCode
import com.devapplab.model.StripeConfig
import com.devapplab.model.match.MatchPlayerStatus
import com.devapplab.model.payment.PaymentAttemptStatus
import com.devapplab.model.payment.PaymentCaptureMethod
import com.devapplab.model.payment.PaymentFailureReason
import com.devapplab.model.payment.PaymentOperationResult
import com.devapplab.model.payment.PaymentProvider
import com.devapplab.model.payment.PaymentStatusResponse
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.createError
import com.stripe.Stripe
import com.stripe.exception.StripeException
import com.stripe.model.Customer
import com.stripe.model.CustomerSession
import com.stripe.model.PaymentIntent
import com.stripe.param.CustomerSessionCreateParams
import com.stripe.param.PaymentIntentCaptureParams
import com.stripe.param.PaymentIntentCreateParams
import io.ktor.http.*
import org.slf4j.LoggerFactory
import java.util.Locale
import java.util.UUID

class StripePaymentService(
    private val stripeConfig: StripeConfig,
    private val paymentRepository: PaymentRepository,
    private val matchRepository: MatchRepository
) : PaymentService {

    private val logger = LoggerFactory.getLogger(this::class.java)

    init {
        Stripe.apiKey = stripeConfig.apiKey
        logger.info("💳 StripePaymentService initialized")
    }

    override suspend fun createPaymentIntent(
        amount: Long,
        currency: String,
        metadata: Map<String, String>,
        captureMethod: PaymentCaptureMethod,
        customerId: String?
    ): PaymentOperationResult {

        logger.info(
            "💳 Creating Stripe PaymentIntent. amount={}, currency={}, captureMethod={}, customerIdPresent={}",
            amount,
            currency,
            captureMethod,
            !customerId.isNullOrBlank()
        )

        val safeCustomerId = customerId?.takeIf { it.isNotBlank() }
        if (safeCustomerId == null) {
            logger.warn("⚠️ Missing customerId. BillingService should create/return one before calling createPaymentIntent.")
            return PaymentOperationResult.Failure(
                PaymentFailureReason.PROVIDER_ERROR,
                "Missing customerId"
            )
        }

        return try {
            val customer = Customer.retrieve(safeCustomerId)

            // ✅ Create CustomerSession (for saved methods / redisplay / save)
            val customerSessionParams = CustomerSessionCreateParams.builder()
                .setCustomer(customer.id)
                .setComponents(
                    CustomerSessionCreateParams.Components.builder()
                        .setPaymentElement(
                            CustomerSessionCreateParams.Components.PaymentElement.builder()
                                .setEnabled(true)
                                .setFeatures(
                                    CustomerSessionCreateParams.Components.PaymentElement.Features.builder()
                                        .setPaymentMethodRedisplay(
                                            CustomerSessionCreateParams.Components.PaymentElement.Features.PaymentMethodRedisplay.ENABLED
                                        )
                                        .setPaymentMethodSave(
                                            CustomerSessionCreateParams.Components.PaymentElement.Features.PaymentMethodSave.ENABLED
                                        )
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .build()

            logger.info("➡️ Creating customer session...")
            val customerSession: CustomerSession = CustomerSession.create(customerSessionParams)

            val capture = when (captureMethod) {
                PaymentCaptureMethod.AUTOMATIC -> PaymentIntentCreateParams.CaptureMethod.AUTOMATIC
                PaymentCaptureMethod.MANUAL -> PaymentIntentCreateParams.CaptureMethod.MANUAL
            }

            val params = PaymentIntentCreateParams.builder()
                .setAmount(amount)
                .setCurrency(currency)
                .setCustomer(customer.id)
                .putAllMetadata(metadata)
                .setCaptureMethod(capture)
                .setAutomaticPaymentMethods(
                    PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true)
                        .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                        .build()
                )
                .build()

            logger.info("➡️ Creating payment intent...")
            val intent: PaymentIntent = PaymentIntent.create(params)

            val clientSecret = intent.clientSecret
            if (clientSecret.isNullOrBlank()) {
                logger.error("🔥 Stripe returned PaymentIntent without client_secret. paymentId={}", intent.id)
                return PaymentOperationResult.Failure(
                    PaymentFailureReason.PROVIDER_ERROR,
                    "Missing clientSecret from Stripe"
                )
            }

            val sessionClientSecret = customerSession.clientSecret
            if (sessionClientSecret.isNullOrBlank()) {
                logger.error("🔥 Stripe returned CustomerSession without client_secret. customerId={}", customer.id)
                return PaymentOperationResult.Failure(
                    PaymentFailureReason.PROVIDER_ERROR,
                    "Missing customerSessionClientSecret from Stripe"
                )
            }

            logger.info(
                "✅ Stripe PaymentIntent created successfully. paymentId={}, status={}, amount={}",
                intent.id,
                intent.status,
                intent.amount
            )

            PaymentOperationResult.Success(
                PaymentIntentResult(
                    clientSecret = clientSecret,
                    paymentId = intent.id,
                    provider = PaymentProvider.STRIPE,
                    customer = customer.id,
                    customerSessionClientSecret = sessionClientSecret,
                    publishableKey = stripeConfig.publishableKey
                )
            )

        } catch (e: StripeException) {
            logger.error(
                "🔥 Stripe error creating payment. statusCode={}, requestId={}, code={}, param={}, message={}",
                e.statusCode,
                e.requestId,
                e.stripeError?.code,
                e.stripeError?.param,
                e.stripeError?.message,
                e
            )

            val reason = when (e.stripeError?.code) {
                "card_declined" -> PaymentFailureReason.DECLINED
                else -> PaymentFailureReason.PROVIDER_ERROR
            }

            PaymentOperationResult.Failure(reason, e.stripeError?.message)

        } catch (e: Exception) {
            logger.error("🔥 Unexpected error creating payment", e)
            PaymentOperationResult.Failure(PaymentFailureReason.UNKNOWN, e.message)
        }
    }

    override suspend fun confirmPayment(paymentId: String): PaymentAttemptStatus {
        logger.info("💳 Confirming Stripe PaymentIntent status. paymentId={}", paymentId)

        return try {
            val intent = PaymentIntent.retrieve(paymentId)

            val status = when (intent.status) {
                "succeeded" -> PaymentAttemptStatus.SUCCEEDED
                "canceled" -> PaymentAttemptStatus.CANCELED

                "requires_payment_method",
                "requires_confirmation",
                "requires_action",
                "processing",
                "requires_capture" -> PaymentAttemptStatus.CREATED

                else -> PaymentAttemptStatus.FAILED
            }

            logger.info(
                "ℹ️ Stripe PaymentIntent status retrieved. paymentId={}, stripeStatus={}, mappedStatus={}",
                paymentId,
                intent.status,
                status
            )

            status
        } catch (e: Exception) {
            logger.error("🔥 Failed to retrieve Stripe PaymentIntent status. paymentId={}", paymentId, e)
            PaymentAttemptStatus.FAILED
        }
    }

    override suspend fun capturePayment(paymentId: String, amount: Long): Boolean {
        logger.info("💳 Capturing Stripe PaymentIntent. paymentId={}, amount={}", paymentId, amount)

        return try {
            val intent = PaymentIntent.retrieve(paymentId)

            val params = PaymentIntentCaptureParams.builder()
                .setAmountToCapture(amount)
                .build()

            val capturedIntent = intent.capture(params)

            if (capturedIntent.status == "succeeded") {
                logger.info("✅ Stripe PaymentIntent captured successfully. paymentId={}", paymentId)
                true
            } else {
                logger.warn(
                    "⚠️ Stripe PaymentIntent capture status not succeeded. status={}, paymentId={}",
                    capturedIntent.status,
                    paymentId
                )
                false
            }
        } catch (e: StripeException) {
            logger.error(
                "🔥 Stripe error capturing payment. statusCode={}, requestId={}, code={}, message={}",
                e.statusCode,
                e.requestId,
                e.stripeError?.code,
                e.stripeError?.message,
                e
            )
            false
        } catch (e: Exception) {
            logger.error("🔥 Unexpected error capturing payment. paymentId={}", paymentId, e)
            false
        }
    }

    override suspend fun cancelPayment(paymentId: String): Boolean {
        logger.info("💳 Canceling Stripe PaymentIntent. paymentId={}", paymentId)

        return try {
            val intent = PaymentIntent.retrieve(paymentId)

            if (intent.status == "succeeded") {
                logger.warn(
                    "⚠️ Cannot cancel PaymentIntent because it is already SUCCEEDED. paymentId={}",
                    paymentId
                )
                return false
            }

            if (intent.status == "canceled") {
                logger.info("ℹ️ PaymentIntent is already canceled. paymentId={}", paymentId)
                return true
            }

            val canceledIntent = intent.cancel()

            if (canceledIntent.status == "canceled") {
                logger.info("✅ Stripe PaymentIntent canceled successfully. paymentId={}", paymentId)
                true
            } else {
                logger.warn(
                    "⚠️ Stripe PaymentIntent cancel status not canceled. status={}, paymentId={}",
                    canceledIntent.status,
                    paymentId
                )
                false
            }
        } catch (e: StripeException) {
            logger.error(
                "🔥 Stripe error canceling payment. statusCode={}, requestId={}, code={}, message={}",
                e.statusCode,
                e.requestId,
                e.stripeError?.code,
                e.stripeError?.message,
                e
            )
            false
        } catch (e: Exception) {
            logger.error("🔥 Unexpected error canceling payment. paymentId={}", paymentId, e)
            false
        }
    }

    override suspend fun recoverPaymentStatus(matchId: String, userId: UUID, locale: Locale): AppResult<PaymentStatusResponse?> {
        val matchUuid = try {
            UUID.fromString(matchId)
        } catch (e: IllegalArgumentException) {
            return locale.createError(
                titleKey = StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                descriptionKey = StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY,
                status = HttpStatusCode.BadRequest
            )
        }

        // 1. Find active payment for this user and match
        val activePayment = paymentRepository.getActivePaymentForPlayer(matchUuid, userId) ?: return AppResult.Success(null)

        val providerPaymentId = activePayment.providerPaymentId ?: return AppResult.Success(
            PaymentStatusResponse(
                paymentId = activePayment.paymentId.toString(),
                providerPaymentId = null,
                clientSecret = activePayment.clientSecret,
                status = activePayment.status,
                provider = activePayment.provider
            )
        )

        // 2. Check with Stripe
        val stripeStatus = try {
            val intent = PaymentIntent.retrieve(providerPaymentId)
            when (intent.status) {
                "succeeded" -> PaymentAttemptStatus.SUCCEEDED
                "canceled" -> PaymentAttemptStatus.CANCELED
                "requires_capture" -> PaymentAttemptStatus.AUTHORIZED
                "processing" -> PaymentAttemptStatus.CREATED
                "requires_payment_method", "requires_confirmation", "requires_action" -> PaymentAttemptStatus.CREATED
                else -> PaymentAttemptStatus.FAILED
            }
        } catch (e: Exception) {
            logger.error("🔥 Failed to retrieve Stripe PaymentIntent status. paymentId={}", providerPaymentId, e)
            activePayment.status
        }

        // 3. Update local DB if status changed
        if (stripeStatus != activePayment.status) {
            paymentRepository.updatePaymentStatus(providerPaymentId, stripeStatus)

            if (stripeStatus == PaymentAttemptStatus.SUCCEEDED || stripeStatus == PaymentAttemptStatus.AUTHORIZED) {
                val matchPlayerId = paymentRepository.getMatchPlayerIdByPaymentId(providerPaymentId)
                if (matchPlayerId != null) {
                    matchRepository.updatePlayerStatus(matchPlayerId, MatchPlayerStatus.JOINED)
                }
            }
        }

        return AppResult.Success(
            PaymentStatusResponse(
                paymentId = activePayment.paymentId.toString(),
                providerPaymentId = providerPaymentId,
                clientSecret = activePayment.clientSecret,
                status = stripeStatus,
                provider = activePayment.provider
            )
        )
    }

    override suspend fun validatePaymentStatus(providerPaymentId: String, locale: Locale): AppResult<PaymentStatusResponse> {
        val paymentInfo = paymentRepository.getPaymentByProviderId(providerPaymentId) ?: return locale.createError(
            titleKey = StringResourcesKey.PAYMENT_NOT_FOUND_TITLE,
            descriptionKey = StringResourcesKey.PAYMENT_NOT_FOUND_DESCRIPTION,
            status = HttpStatusCode.NotFound,
            errorCode = ErrorCode.NOT_FOUND
        )

        val stripeStatus = try {
            val intent = PaymentIntent.retrieve(providerPaymentId)
            when (intent.status) {
                "succeeded" -> PaymentAttemptStatus.SUCCEEDED
                "canceled" -> PaymentAttemptStatus.CANCELED
                "requires_capture" -> PaymentAttemptStatus.AUTHORIZED
                "processing" -> PaymentAttemptStatus.CREATED
                "requires_payment_method", "requires_confirmation", "requires_action" -> PaymentAttemptStatus.CREATED
                else -> PaymentAttemptStatus.FAILED
            }
        } catch (e: Exception) {
            logger.error("🔥 Failed to retrieve Stripe PaymentIntent status. paymentId={}", providerPaymentId, e)
            paymentInfo.status
        }

        if (stripeStatus != paymentInfo.status) {
            paymentRepository.updatePaymentStatus(providerPaymentId, stripeStatus)

            if (stripeStatus == PaymentAttemptStatus.SUCCEEDED || stripeStatus == PaymentAttemptStatus.AUTHORIZED) {
                val matchPlayerId = paymentRepository.getMatchPlayerIdByPaymentId(providerPaymentId)
                if (matchPlayerId != null) {
                    matchRepository.updatePlayerStatus(matchPlayerId, MatchPlayerStatus.JOINED)
                }
            }
        }

        return AppResult.Success(
            PaymentStatusResponse(
                paymentId = paymentInfo.paymentId.toString(),
                providerPaymentId = paymentInfo.providerPaymentId,
                clientSecret = paymentInfo.clientSecret,
                status = stripeStatus,
                provider = paymentInfo.provider
            )
        )
    }
}
