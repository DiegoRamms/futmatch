package com.devapplab.model.match.response

import com.devapplab.model.payment.PaymentProvider
import kotlinx.serialization.Serializable

@Serializable
data class JoinMatchResponse(
    val clientSecret: String?,
    val paymentId: String?,
    val provider: PaymentProvider,
    val amountInCents: Long,
    val currency: String,
    val customer: String?,
    val customerSessionClientSecret: String?,
    val publishableKey: String?,
    val reservationTtlMs: Long
)
