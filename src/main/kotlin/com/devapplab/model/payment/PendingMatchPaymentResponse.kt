package com.devapplab.model.payment

import kotlinx.serialization.Serializable

@Serializable
data class PendingMatchPaymentResponse(
    val clientSecret: String,
    val paymentId: String,
    val provider: PaymentProvider,
    val amountInCents: Long,
    val currency: String,
    val customer: String,
    val customerSessionClientSecret: String,
    val publishableKey: String,
    val reservationTtlMs: Long,
    val existingPaymentStatus: PaymentAttemptStatus
)
