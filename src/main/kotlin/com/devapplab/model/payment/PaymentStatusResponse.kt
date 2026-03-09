package com.devapplab.model.payment

import kotlinx.serialization.Serializable

@Serializable
data class PaymentStatusResponse(
    val paymentId: String,
    val providerPaymentId: String?,
    val clientSecret: String?,
    val status: PaymentAttemptStatus,
    val provider: PaymentProvider
)
