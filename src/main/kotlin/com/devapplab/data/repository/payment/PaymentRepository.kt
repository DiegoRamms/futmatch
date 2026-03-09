package com.devapplab.data.repository.payment

import com.devapplab.model.payment.PaymentAttemptStatus
import com.devapplab.model.payment.PaymentProvider
import java.math.BigDecimal
import java.util.UUID

interface PaymentRepository {
    suspend fun createPayment(
        matchPlayerId: UUID,
        provider: PaymentProvider,
        providerPaymentId: String?,
        clientSecret: String?,
        amount: BigDecimal,
        currency: String,
        status: PaymentAttemptStatus
    ): UUID

    suspend fun updatePaymentStatus(
        providerPaymentId: String,
        status: PaymentAttemptStatus,
        failureCode: String? = null,
        failureMessage: String? = null
    ): Boolean

    suspend fun getMatchPlayerIdByPaymentId(providerPaymentId: String): UUID?

    suspend fun getPendingCapturePayments(
        startTimeWindow: Long,
        endTimeWindow: Long
    ): List<PendingPaymentInfo>

    suspend fun getActivePaymentForPlayer(matchId: UUID, userId: UUID): PaymentInfo?

    suspend fun getActivePaymentByMatchPlayerId(matchPlayerId: UUID): PaymentInfo?
}

data class PendingPaymentInfo(
    val paymentId: UUID,
    val providerPaymentId: String,
    val matchPlayerId: UUID,
    val matchId: UUID,
    val userId: UUID,
    val amount: BigDecimal,
    val currency: String
)

data class PaymentInfo(
    val paymentId: UUID,
    val providerPaymentId: String?,
    val clientSecret: String?,
    val status: PaymentAttemptStatus,
    val provider: PaymentProvider
)
