package com.devapplab.service.payment

import com.devapplab.data.repository.match.MatchRepository
import com.devapplab.data.repository.payment.PaymentRepository
import com.devapplab.data.repository.payment.StripeWebhookEventRepository
import com.devapplab.features.match.MatchUpdateBus
import com.devapplab.model.WebhookConfig
import com.devapplab.model.match.MatchPlayerStatus
import com.devapplab.model.payment.PaymentAttemptStatus
import com.devapplab.service.firebase.MatchSignalsService
import com.stripe.exception.SignatureVerificationException
import com.stripe.model.Event
import com.stripe.model.PaymentIntent
import com.stripe.net.Webhook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.util.UUID

class StripeWebhookService(
    private val paymentRepository: PaymentRepository,
    private val matchRepository: MatchRepository,
    private val matchUpdateBus: MatchUpdateBus,
    private val matchSignalsService: MatchSignalsService,
    private val stripeWebhookEventRepository: StripeWebhookEventRepository,
    private val webhookConfig: WebhookConfig,
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun handleWebhook(payload: String, signature: String): Boolean {
        var lockedEventId: String? = null

        try {
            val event = Webhook.constructEvent(payload, signature, webhookConfig.webhookSecret)
            logger.info("💳 Stripe webhook received. eventId={}, type={}", event.id, event.type)

            val locked = stripeWebhookEventRepository.tryLock(event.id)
            if (!locked) {
                logger.info("ℹ️ Stripe webhook ignored (already processed). eventId={}", event.id)
                return true
            }
            lockedEventId = event.id

            val paymentIntent = extractPaymentIntent(event, payload)
            if (paymentIntent == null) {
                logger.warn(
                    "⚠️ Stripe webhook ignored (could not obtain PaymentIntent). eventId={}, type={}",
                    event.id, event.type
                )
                return true
            }

            when (event.type) {
                // ✅ "Payment confirmed" (can be succeeded even if capture is manual; in your case you capture later)
                "payment_intent.succeeded" -> handlePaymentSucceeded(paymentIntent)

                // ✅ "Payment failed"
                "payment_intent.payment_failed" -> handlePaymentFailed(paymentIntent)

                // ✅ Very common in manual capture: transitions to requires_capture and sends this event
                "payment_intent.amount_capturable_updated" -> handleAmountCapturableUpdated(paymentIntent)

                // ✅ If you want to consider "paid" until capture
                "charge.captured" -> handleChargeCaptured(paymentIntent)

                // ✅ Handle cancellation
                "payment_intent.canceled" -> handlePaymentCanceled(paymentIntent)

                else -> {
                    logger.info("ℹ️ Stripe webhook type ignored. eventId={}, type={}", event.id, event.type)
                }
            }

            logger.info("✅ Stripe webhook processed successfully. eventId={}", event.id)
            return true

        } catch (_: SignatureVerificationException) {
            logger.error("❌ Stripe webhook signature verification failed")
            return false

        } catch (e: Exception) {
            logger.error("🔥 Stripe webhook processing failed. eventId={}", lockedEventId, e)

            if (lockedEventId != null) {
                stripeWebhookEventRepository.unlock(lockedEventId)
                logger.warn("🔓 Stripe webhook lock released. eventId={}", lockedEventId)
            }
            return false
        }
    }

    /**
     * PRO:
     * 1) Attempts to deserialize event.data.object to PaymentIntent (happy path).
     * 2) If it fails (API version mismatch / payload mismatch), extracts piId from payload (JSON) and retrieves it.
     *
     * Supports both:
     * - payment_intent.*  -> data.object.id = "pi_..."
     * - charge.*         -> data.object.payment_intent = "pi_..."
     */
    private suspend fun extractPaymentIntent(event: Event, payload: String): PaymentIntent? {
        // 1) Parse PaymentIntent ID from payload
        val piId = extractPaymentIntentIdFromPayload(payload)
        if (piId.isNullOrBlank()) {
            logger.warn(
                "⚠️ Could not extract PaymentIntent id from payload. eventId={}, type={}",
                event.id, event.type
            )
            return null
        }

        // 2) Retrieve from Stripe (IO) - Always fetch to ensure latest state and avoid deserialization issues
        return withContext(Dispatchers.IO) {
            runCatching { PaymentIntent.retrieve(piId) }
                .onFailure {
                    logger.error(
                        "🔥 Failed to retrieve PaymentIntent from Stripe. eventId={}, piId={}",
                        event.id, piId, it
                    )
                }
                .getOrNull()
        }
    }

    private fun extractPaymentIntentIdFromPayload(payload: String): String? {
        return runCatching {
            val root = json.parseToJsonElement(payload).jsonObject
            val obj = root["data"]?.jsonObject
                ?.get("object")?.jsonObject
                ?: return null

            // payment_intent.* => id
            val directId = obj["id"]?.jsonPrimitive?.content
            if (!directId.isNullOrBlank() && directId.startsWith("pi_")) return directId

            // charge.* => payment_intent
            val fromCharge = obj["payment_intent"]?.jsonPrimitive?.content
            if (!fromCharge.isNullOrBlank() && fromCharge.startsWith("pi_")) return fromCharge

            null
        }.getOrNull()
    }

    private suspend fun handlePaymentSucceeded(paymentIntent: PaymentIntent) {
        val paymentIntentId = paymentIntent.id ?: return
        logger.info("💰 payment_intent.succeeded. paymentIntentId={}", paymentIntentId)

        val matchPlayerId = paymentRepository.getMatchPlayerIdByPaymentId(paymentIntentId)
        if (matchPlayerId == null) {
            logger.error("❌ MatchPlayerId not found. paymentIntentId={}", paymentIntentId)
            return
        }

        val paymentUpdated = paymentRepository.updatePaymentStatus(
            providerPaymentId = paymentIntentId,
            status = PaymentAttemptStatus.SUCCEEDED
        )
        if (!paymentUpdated) {
            logger.error("❌ Failed to update payment status. paymentIntentId={}", paymentIntentId)
            return
        }

        val playerUpdated = matchRepository.updatePlayerStatus(matchPlayerId, MatchPlayerStatus.JOINED)
        if (!playerUpdated) {
            logger.error("❌ Failed to update player status. matchPlayerId={}", matchPlayerId)
            return
        }

        logger.info("✅ Player marked as JOINED. matchPlayerId={}", matchPlayerId)

        paymentIntent.metadata["matchId"]?.let { matchIdStr ->
            val matchId = UUID.fromString(matchIdStr)
            publishMatchUpdate(matchId)
            logger.info("📡 Match update sent. matchId={}", matchId)
        }
    }

    private suspend fun handlePaymentFailed(paymentIntent: PaymentIntent) {
        val paymentIntentId = paymentIntent.id ?: return
        logger.warn("⚠️ payment_intent.payment_failed. paymentIntentId={}", paymentIntentId)

        paymentRepository.updatePaymentStatus(
            providerPaymentId = paymentIntentId,
            status = PaymentAttemptStatus.FAILED,
            failureCode = paymentIntent.lastPaymentError?.code,
            failureMessage = paymentIntent.lastPaymentError?.message
        )

        paymentIntent.metadata["matchId"]?.let { matchIdStr ->
            val matchId = UUID.fromString(matchIdStr)
            publishMatchUpdate(matchId)
            logger.info("📡 Match update sent after failure. matchId={}", matchId)
        }
    }

    /**
     * For manual capture: this event arrives when funds are already authorized (requires_capture).
     * Here we update the status to AUTHORIZED so the Job knows it can capture.
     */
    private suspend fun handleAmountCapturableUpdated(paymentIntent: PaymentIntent) {
        val paymentIntentId = paymentIntent.id ?: return
        logger.info("🟡 payment_intent.amount_capturable_updated. paymentIntentId={}, status={}", paymentIntentId, paymentIntent.status)

        // ✅ Update to AUTHORIZED: This enables the Job to capture the payment.
        val updated = paymentRepository.updatePaymentStatus(
            providerPaymentId = paymentIntentId,
            status = PaymentAttemptStatus.AUTHORIZED
        )

        if (updated) {
            logger.info("✅ Payment marked as AUTHORIZED (ready for capture). paymentIntentId={}", paymentIntentId)
        } else {
            logger.error("❌ Failed to mark payment as AUTHORIZED. paymentIntentId={}", paymentIntentId)
        }

        // ✅ Also mark player as JOINED because funds are secured (authorized)
        val matchPlayerId = paymentRepository.getMatchPlayerIdByPaymentId(paymentIntentId)
        if (matchPlayerId != null) {
            val playerUpdated = matchRepository.updatePlayerStatus(matchPlayerId, MatchPlayerStatus.JOINED)
            if (playerUpdated) {
                logger.info("✅ Player marked as JOINED (Authorized). matchPlayerId={}", matchPlayerId)
            } else {
                logger.error("❌ Failed to update player status to JOINED. matchPlayerId={}", matchPlayerId)
            }
        } else {
            logger.warn("⚠️ Could not find matchPlayerId for authorized payment. paymentIntentId={}", paymentIntentId)
        }

        paymentIntent.metadata["matchId"]?.let { matchIdStr ->
            val matchId = UUID.fromString(matchIdStr)
            publishMatchUpdate(matchId)
            logger.info("📡 Match update sent after amount_capturable_updated. matchId={}", matchId)
        }
    }

    private suspend fun handlePaymentCanceled(paymentIntent: PaymentIntent) {
        val paymentIntentId = paymentIntent.id ?: return
        logger.info("🚫 payment_intent.canceled. paymentIntentId={}", paymentIntentId)

        val updated = paymentRepository.updatePaymentStatus(
            providerPaymentId = paymentIntentId,
            status = PaymentAttemptStatus.CANCELED
        )

        if (updated) {
            logger.info("✅ Payment marked as CANCELED in DB. paymentIntentId={}", paymentIntentId)
        } else {
            logger.warn("⚠️ Failed to mark payment as CANCELED (maybe not found?). paymentIntentId={}", paymentIntentId)
        }

        // Note: We don't necessarily need to update player status here because usually
        // the cancellation is triggered by 'leaveMatch' which already removes the player.
        // But if cancellation happens from Stripe Dashboard, we might want to ensure player is removed.
        // For now, we just update payment status to keep DB consistent.
    }

    /**
     * If your business defines "paid" until captured (manual capture),
     * then here is where you would mark PAID instead of doing it in succeeded.
     */
    private suspend fun handleChargeCaptured(paymentIntent: PaymentIntent) {
        val paymentIntentId = paymentIntent.id ?: return
        logger.info("🟢 charge.captured (mapped to PI). paymentIntentId={}", paymentIntentId)

        // If you want, you can repeat the same logic as succeeded here:
        // handlePaymentSucceeded(paymentIntent)
        // Or just update the UI:
        paymentIntent.metadata["matchId"]?.let { matchIdStr ->
            val matchId = UUID.fromString(matchIdStr)
            publishMatchUpdate(matchId)
            logger.info("📡 Match update sent after charge.captured. matchId={}", matchId)
        }
    }

    private suspend fun publishMatchUpdate(matchId: UUID) {
        withContext(Dispatchers.Default) {
            matchUpdateBus.publish(matchId)
            matchSignalsService.signalMatchUpdateUpsert(matchId.toString())
        }
    }
}