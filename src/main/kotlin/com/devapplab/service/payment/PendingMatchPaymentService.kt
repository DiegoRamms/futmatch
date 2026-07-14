package com.devapplab.service.payment

import com.devapplab.data.repository.match.MatchRepository
import com.devapplab.data.repository.payment.PaymentRepository
import com.devapplab.model.AppResult
import com.devapplab.model.ErrorCode
import com.devapplab.model.match.MatchPlayerStatus
import com.devapplab.model.payment.PendingMatchPaymentResponse
import com.devapplab.observability.AppRequestContext
import com.devapplab.observability.appRejected
import com.devapplab.observability.appSuccess
import com.devapplab.service.billing.BillingService
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.createError
import io.ktor.http.HttpStatusCode
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.util.Locale
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

class PendingMatchPaymentService(
    private val matchRepository: MatchRepository,
    private val paymentRepository: PaymentRepository,
    private val billingService: BillingService
) {
    private companion object {
        val RESERVATION_TTL = 5.minutes
    }

    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun getPendingPayment(
        matchId: UUID,
        userId: UUID,
        locale: Locale,
        context: AppRequestContext
    ): AppResult<PendingMatchPaymentResponse> {
        val match = matchRepository.getMatchById(matchId)
            ?: run {
                logger.appRejected(
                    event = "payment.match_pending.load_failed",
                    context = context,
                    reason = "match_not_found",
                    userId = userId,
                    statusCode = HttpStatusCode.NotFound.value,
                    extra = mapOf("matchId" to matchId)
                )
                return locale.createError(
                    titleKey = StringResourcesKey.NOT_FOUND_TITLE,
                    descriptionKey = StringResourcesKey.NOT_FOUND_DESCRIPTION,
                    status = HttpStatusCode.NotFound,
                    errorCode = ErrorCode.NOT_FOUND
                )
            }

        val currentPlayer = match.players.firstOrNull { it.userId == userId }
        if (currentPlayer?.status != MatchPlayerStatus.RESERVED) {
            return pendingPaymentConflict(matchId, userId, locale, context, "user_not_reserved")
        }

        val activePayment = paymentRepository.getActivePaymentForPlayer(matchId, userId)
            ?: return pendingPaymentConflict(matchId, userId, locale, context, "active_payment_not_found")
        val clientSecret = activePayment.clientSecret
            ?: return pendingPaymentConflict(matchId, userId, locale, context, "client_secret_not_found")
        val providerPaymentId = activePayment.providerPaymentId
            ?: return pendingPaymentConflict(matchId, userId, locale, context, "provider_payment_id_not_found")

        val reservationExpiresAt = currentPlayer.joinedAt + RESERVATION_TTL.inWholeMilliseconds
        val remainingReservationTtlMs = (reservationExpiresAt - System.currentTimeMillis()).coerceAtLeast(0L)
        if (remainingReservationTtlMs == 0L) {
            return pendingPaymentConflict(matchId, userId, locale, context, "reservation_expired")
        }

        val customerId = try {
            billingService.getOrCreateCustomer(userId, activePayment.provider)
        } catch (e: Exception) {
            logger.error("Failed to resolve payment customer for pending match payment. userId={}, matchId={}", userId, matchId, e)
            return pendingPaymentTemporarilyUnavailable(matchId, userId, locale, context, "customer_resolution_failed")
        }

        val customerSessionClientSecret = try {
            billingService.createCustomerSession(customerId)
        } catch (e: Exception) {
            logger.error("Failed to create customer session for pending match payment. userId={}, matchId={}", userId, matchId, e)
            return pendingPaymentTemporarilyUnavailable(matchId, userId, locale, context, "customer_session_failed")
        }

        val response = PendingMatchPaymentResponse(
            clientSecret = clientSecret,
            paymentId = providerPaymentId,
            provider = activePayment.provider,
            amountInCents = activePayment.amount.multiply(BigDecimal(100)).toLong(),
            currency = activePayment.currency.lowercase(Locale.ROOT),
            customer = customerId,
            customerSessionClientSecret = customerSessionClientSecret,
            publishableKey = billingService.getPublishableKey(),
            reservationTtlMs = remainingReservationTtlMs,
            existingPaymentStatus = activePayment.status
        )

        logger.appSuccess(
            event = "payment.match_pending.loaded",
            context = context,
            userId = userId,
            statusCode = HttpStatusCode.OK.value,
            extra = mapOf("matchId" to matchId, "paymentId" to response.paymentId)
        )
        return AppResult.Success(response)
    }

    private fun pendingPaymentConflict(
        matchId: UUID,
        userId: UUID,
        locale: Locale,
        context: AppRequestContext,
        reason: String
    ): AppResult.Failure {
        logger.appRejected(
            event = "payment.match_pending.load_failed",
            context = context,
            reason = reason,
            userId = userId,
            statusCode = HttpStatusCode.Conflict.value,
            extra = mapOf("matchId" to matchId)
        )
        return locale.createError(
            titleKey = StringResourcesKey.PAYMENT_NOT_FOUND_TITLE,
            descriptionKey = StringResourcesKey.PAYMENT_NOT_FOUND_DESCRIPTION,
            status = HttpStatusCode.Conflict,
            errorCode = ErrorCode.PAYMENT_PENDING_NOT_RECOVERABLE
        )
    }

    private fun pendingPaymentTemporarilyUnavailable(
        matchId: UUID,
        userId: UUID,
        locale: Locale,
        context: AppRequestContext,
        reason: String
    ): AppResult.Failure {
        logger.appRejected(
            event = "payment.match_pending.load_failed",
            context = context,
            reason = reason,
            userId = userId,
            statusCode = HttpStatusCode.ServiceUnavailable.value,
            extra = mapOf("matchId" to matchId)
        )
        return locale.createError(
            titleKey = StringResourcesKey.PAYMENT_FAILED_TITLE,
            descriptionKey = StringResourcesKey.PAYMENT_FAILED_DESCRIPTION,
            status = HttpStatusCode.ServiceUnavailable,
            errorCode = ErrorCode.PAYMENT_FAILED
        )
    }
}
