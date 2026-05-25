package com.devapplab.model.payment

import kotlinx.serialization.Serializable

@Serializable
data class PaymentPollingStatusResponse(
    val status: PaymentAttemptStatus,
    val isFinal: Boolean,
    val isSuccess: Boolean
)

