package com.devapplab.model.payment

import kotlinx.serialization.Serializable

@Serializable
enum class PaymentCaptureMethod {
    AUTOMATIC,
    MANUAL
}