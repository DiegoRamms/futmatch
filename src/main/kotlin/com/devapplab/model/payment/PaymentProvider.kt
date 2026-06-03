package com.devapplab.model.payment

import kotlinx.serialization.Serializable

@Serializable
enum class PaymentProvider {
    STRIPE,
    OPEN_PAY,
    CASH,
    MANUAL,
    UNKNOWN
}