package com.devapplab.observability

import com.devapplab.model.payment.PaymentAttemptStatus
import com.devapplab.model.payment.PaymentCaptureMethod
import com.devapplab.model.payment.PaymentFailureReason
import com.devapplab.model.payment.PaymentProvider
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer

/**
 * Business-level payment metrics. Labels are deliberately bounded: never add user, match,
 * PaymentIntent, Stripe event IDs, or Stripe error messages here.
 */
class PaymentMetrics(
    private val meterRegistry: MeterRegistry
) {
    fun recordJoin(
        outcome: MatchJoinMetricOutcome,
        provider: PaymentProvider,
        reason: MatchJoinRejectionReason = MatchJoinRejectionReason.NONE,
        captureMethod: PaymentCaptureMethod? = null
    ) {
        Counter.builder("match.join")
            .description("Match join payment-flow outcomes")
            .tags(
                "outcome", outcome.metricValue,
                "provider", provider.metricValue,
                "reason", reason.metricValue,
                "capture_method", captureMethod?.metricValue ?: "none"
            )
            .register(meterRegistry)
            .increment()
    }

    fun recordPaymentIntentCreated(provider: PaymentProvider, captureMethod: PaymentCaptureMethod) {
        Counter.builder("payment.intent.created")
            .description("PaymentIntents created successfully")
            .tags("provider", provider.metricValue, "capture_method", captureMethod.metricValue)
            .register(meterRegistry)
            .increment()
    }

    fun recordPaymentIntentFailed(provider: PaymentProvider, reason: PaymentFailureReason) {
        Counter.builder("payment.intent.failed")
            .description("PaymentIntent creation failures")
            .tags("provider", provider.metricValue, "reason", reason.metricValue)
            .register(meterRegistry)
            .increment()
    }

    fun startPaymentIntentTimer(): Timer.Sample = Timer.start(meterRegistry)

    fun stopPaymentIntentTimer(
        sample: Timer.Sample,
        provider: PaymentProvider,
        captureMethod: PaymentCaptureMethod,
        outcome: String
    ) {
        sample.stop(
            Timer.builder("payment.intent.creation")
                .description("Time spent creating a PaymentIntent and its customer session")
                .tags(
                    "provider", provider.metricValue,
                    "capture_method", captureMethod.metricValue,
                    "outcome", outcome
                )
                .register(meterRegistry)
        )
    }

    fun recordWebhookReceived(eventType: String) {
        Counter.builder("stripe.webhook.received")
            .description("Verified Stripe webhook events received")
            .tag("event_type", eventType.toMetricEventType())
            .register(meterRegistry)
            .increment()
    }

    fun startWebhookTimer(): Timer.Sample = Timer.start(meterRegistry)

    fun stopWebhookTimer(sample: Timer.Sample, eventType: String, outcome: StripeWebhookMetricOutcome) {
        sample.stop(
            Timer.builder("stripe.webhook.processing")
                .description("Stripe webhook processing duration")
                .tags("event_type", eventType.toMetricEventType(), "outcome", outcome.metricValue)
                .register(meterRegistry)
        )
        Counter.builder("stripe.webhook.outcome")
            .description("Stripe webhook processing outcomes")
            .tags("event_type", eventType.toMetricEventType(), "outcome", outcome.metricValue)
            .register(meterRegistry)
            .increment()
    }

    fun recordPaymentTransition(status: PaymentAttemptStatus) {
        Counter.builder("payment.webhook.transition")
            .description("Payment states persisted from Stripe webhook events")
            .tags("provider", PaymentProvider.STRIPE.metricValue, "status", status.metricValue)
            .register(meterRegistry)
            .increment()
    }
}

enum class MatchJoinMetricOutcome(val metricValue: String) {
    RESERVED("reserved"),
    REUSED_PAYMENT("reused_payment"),
    REJECTED("rejected"),
    RESERVATION_FAILED("reservation_failed"),
    PAYMENT_INTENT_FAILED("payment_intent_failed")
}

enum class MatchJoinRejectionReason(val metricValue: String) {
    NONE("none"),
    PENDING_RESERVATION("pending_reservation"),
    MATCH_NOT_FOUND("match_not_found"),
    MATCH_NOT_SCHEDULED("match_not_scheduled"),
    JOIN_TOO_EARLY("join_too_early"),
    ALREADY_JOINED("already_joined"),
    MATCH_FULL("match_full"),
    TEAM_FULL("team_full")
}

enum class StripeWebhookMetricOutcome(val metricValue: String) {
    PROCESSED("processed"),
    DUPLICATE("duplicate"),
    IGNORED_NO_PAYMENT_INTENT("ignored_no_payment_intent"),
    IGNORED_EVENT_TYPE("ignored_event_type"),
    SIGNATURE_INVALID("signature_invalid"),
    FAILED("failed")
}

private val PaymentProvider.metricValue: String
    get() = name.lowercase()

private val PaymentCaptureMethod.metricValue: String
    get() = name.lowercase()

private val PaymentFailureReason.metricValue: String
    get() = name.lowercase()

private val PaymentAttemptStatus.metricValue: String
    get() = name.lowercase()

private fun String.toMetricEventType(): String = when (this) {
    "payment_intent.succeeded",
    "payment_intent.payment_failed",
    "payment_intent.amount_capturable_updated",
    "payment_intent.canceled",
    "charge.captured" -> this
    else -> "other"
}
