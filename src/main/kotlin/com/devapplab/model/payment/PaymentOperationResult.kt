package com.devapplab.model.payment

import com.devapplab.service.payment.PaymentIntentResult

sealed class PaymentOperationResult {
    data class Success(val data: PaymentIntentResult) : PaymentOperationResult()
    data class Failure(val reason: PaymentFailureReason, val message: String? = null) : PaymentOperationResult()
}

enum class PaymentFailureReason {
    DECLINED,
    PROVIDER_ERROR,
    INVALID_DATA,
    UNKNOWN
}