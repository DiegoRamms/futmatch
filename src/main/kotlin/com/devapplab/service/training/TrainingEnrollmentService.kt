package com.devapplab.service.training

import com.devapplab.data.repository.training.TrainingEnrollmentRepository
import com.devapplab.data.repository.training.TrainingSeatResult
import com.devapplab.model.AppResult
import com.devapplab.model.ErrorCode
import com.devapplab.model.MatchPaymentConfig
import com.devapplab.model.match.response.JoinMatchResponse
import com.devapplab.model.payment.PaymentAttemptStatus
import com.devapplab.model.payment.PaymentOperationResult
import com.devapplab.model.payment.PaymentProvider
import com.devapplab.service.billing.BillingService
import com.devapplab.service.payment.PaymentService
import com.devapplab.utils.createError
import io.ktor.http.HttpStatusCode
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class TrainingEnrollmentService(
    private val repository: TrainingEnrollmentRepository,
    private val billing: BillingService,
    private val payments: PaymentService,
    private val config: MatchPaymentConfig
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val captureWindow = config.captureWindowHours.hours.inWholeMilliseconds

    suspend fun join(training: UUID, player: UUID, locale: Locale): AppResult<JoinMatchResponse> {
        val result = repository.reserve(
            training, player, System.currentTimeMillis(),
            config.maxJoinPaymentWindowHours.hours.inWholeMilliseconds,
            captureWindow, 5.minutes.inWholeMilliseconds
        )
        val enrollment = when (result) {
            is TrainingSeatResult.Rejected -> return locale.createError(
                status = if (result.error == ErrorCode.NOT_FOUND) HttpStatusCode.NotFound else HttpStatusCode.Conflict,
                errorCode = result.error
            )
            is TrainingSeatResult.Acquired -> result.enrollment
        }
        if (enrollment.status != PaymentAttemptStatus.CREATED) {
            return AppResult.Success(JoinMatchResponse(
                null, enrollment.providerId, PaymentProvider.STRIPE, enrollment.amount, "mxn",
                null, null, null, 0, true, enrollment.status
            ))
        }

        val checkout = try {
            withContext(Dispatchers.IO) {
                payments.createPaymentIntent(
                    amount = enrollment.amount,
                    currency = "mxn",
                    metadata = mapOf(
                        "trainingId" to training.toString(),
                        "trainingEnrollmentId" to enrollment.id.toString(),
                        "userId" to player.toString()
                    ),
                    captureMethod = enrollment.captureMethod,
                    customerId = billing.getOrCreateCustomer(player, PaymentProvider.STRIPE),
                    idempotencyKey = "training-enrollment-${enrollment.id}"
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logger.warn("training.checkout.unavailable enrollmentId={} type={}", enrollment.id, error.javaClass.simpleName)
            return locale.paymentError()
        }
        if (checkout !is PaymentOperationResult.Success) return locale.paymentError()
        val intent = checkout.data
        if (!repository.attach(enrollment.id, intent.paymentId)) {
            // An expiry, cancellation, or a webhook can win while Stripe responds.
            // A confirmed enrollment must keep the provider payment.
            val current = repository.status(training, player)
            if (current?.providerId != intent.paymentId || current.cancelRequested) {
                cancelAtProvider(intent.paymentId)
            }
            return locale.paymentError()
        }
        return AppResult.Success(JoinMatchResponse(
            intent.clientSecret, intent.paymentId, intent.provider, enrollment.amount,
            "mxn", intent.customer, intent.customerSessionClientSecret, intent.publishableKey,
            (enrollment.expiresAt - System.currentTimeMillis()).coerceAtLeast(0)
        ))
    }

    suspend fun refreshPayment(training: UUID, player: UUID): AppResult<Boolean> {
        val current = repository.status(training, player)
        if (current?.status == PaymentAttemptStatus.CREATED && !current.cancelRequested) {
            current.providerId?.let { pollStripe(it) }
        }
        return AppResult.Success(true)
    }

    suspend fun leave(training: UUID, player: UUID, locale: Locale): AppResult<Boolean> {
        if (!repository.leave(training, player, System.currentTimeMillis(), captureWindow)) {
            return locale.createError(status = HttpStatusCode.Conflict, errorCode = ErrorCode.MATCH_NOT_SCHEDULED)
        }
        repository.status(training, player)?.takeIf { it.cancelRequested }?.providerId?.let { cancelAtProvider(it) }
        return AppResult.Success(true)
    }

    private suspend fun pollStripe(provider: String) =
        repository.updatePayment(provider, withContext(Dispatchers.IO) { payments.confirmPayment(provider) })

    private suspend fun cancelAtProvider(provider: String) {
        if (withContext(Dispatchers.IO) { payments.cancelPayment(provider) }) {
            repository.updatePayment(provider, PaymentAttemptStatus.CANCELED)
        } else {
            pollStripe(provider)
        }
    }

    suspend fun reconcilePayments() {
        repository.dueForCleanup(System.currentTimeMillis()).forEach { cancelAtProvider(it) }
    }

    suspend fun refundCancelledPayments() {
        repository.dueForRefund().forEach { provider ->
            if (withContext(Dispatchers.IO) { payments.refundPayment(provider, idempotencyKey = "training-cancel-$provider") }) {
                repository.updatePayment(provider, PaymentAttemptStatus.REFUNDED)
            }
        }
    }

    suspend fun capturePayments() {
        repository.dueForCapture(System.currentTimeMillis(), captureWindow).forEach { enrollment ->
            val provider = enrollment.providerId ?: return@forEach
            if (withContext(Dispatchers.IO) { payments.capturePayment(provider, enrollment.amount) }) {
                repository.updatePayment(provider, PaymentAttemptStatus.SUCCEEDED)
            } else {
                pollStripe(provider)
            }
        }
    }

    private fun Locale.paymentError() =
        createError(status = HttpStatusCode.ServiceUnavailable, errorCode = ErrorCode.PAYMENT_FAILED)
}
