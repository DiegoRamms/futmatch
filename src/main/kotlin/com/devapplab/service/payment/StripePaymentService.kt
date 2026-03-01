package com.devapplab.service.payment

import com.devapplab.model.payment.*
import com.stripe.Stripe
import com.stripe.exception.StripeException
import com.stripe.model.Customer
import com.stripe.model.CustomerSession
import com.stripe.model.PaymentIntent
import com.stripe.param.CustomerCreateParams
import com.stripe.param.CustomerSessionCreateParams
import com.stripe.param.PaymentIntentCaptureParams
import com.stripe.param.PaymentIntentCreateParams
import org.slf4j.LoggerFactory

class StripePaymentService(
    private val apiKey: String,
    private val publishableKey: String
) : PaymentService {

    private val logger = LoggerFactory.getLogger(this::class.java)

    init {
        Stripe.apiKey = apiKey
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
            "💳 Creating Stripe PaymentIntent. amount={}, currency={}, captureMethod={}, customerId={}",
            amount,
            currency,
            captureMethod,
            customerId
        )

        return try {
            val customer = if (customerId != null) {
                logger.info("➡️ Creating/Retrieving customer...")
                Customer.retrieve(customerId)

            } else {
                val customerParams = CustomerCreateParams.builder().build()
                Customer.create(customerParams)
            }

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
            val customerSession = CustomerSession.create(customerSessionParams)


            val params = PaymentIntentCreateParams.builder()
                .setAmount(amount)
                .setCurrency(currency)
                .setCustomer(customer.id)
                .putAllMetadata(metadata)
                .setCaptureMethod(
                    when (captureMethod) {
                        PaymentCaptureMethod.AUTOMATIC ->
                            PaymentIntentCreateParams.CaptureMethod.AUTOMATIC

                        PaymentCaptureMethod.MANUAL ->
                            PaymentIntentCreateParams.CaptureMethod.MANUAL
                    }
                )
                .setAutomaticPaymentMethods(
                    PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true)
                        .setAllowRedirects(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER
                        )
                        .build()
                )
                .build()
            logger.info("➡️ Creating payment intent...")
            val intent = PaymentIntent.create(params)

            logger.info(
                "✅ Stripe PaymentIntent created successfully. paymentId={}, status={}, amount={}",
                intent.id,
                intent.status,
                intent.amount
            )

            PaymentOperationResult.Success(
                PaymentIntentResult(
                    clientSecret = intent.clientSecret,
                    paymentId = intent.id,
                    provider = PaymentProvider.STRIPE,
                    customer = customer.id,
                    customerSessionClientSecret = customerSession.clientSecret,
                    publishableKey = publishableKey
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

    override suspend fun confirmPayment(
        paymentId: String
    ): PaymentAttemptStatus {

        logger.info(
            "💳 Confirming Stripe PaymentIntent status. paymentId={}",
            paymentId
        )

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

            logger.error(
                "🔥 Failed to retrieve Stripe PaymentIntent status. paymentId={}",
                paymentId,
                e
            )
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
                logger.warn("⚠️ Stripe PaymentIntent capture status not succeeded. status={}, paymentId={}", capturedIntent.status, paymentId)
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

            // ✅ Check status before cancelling to avoid "payment_intent_unexpected_state"
            if (intent.status == "succeeded") {
                logger.warn("⚠️ Cannot cancel PaymentIntent because it is already SUCCEEDED (User will not be refunded automatically). paymentId={}", paymentId)
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
                logger.warn("⚠️ Stripe PaymentIntent cancel status not canceled. status={}, paymentId={}", canceledIntent.status, paymentId)
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
}