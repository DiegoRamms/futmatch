package com.devapplab.model.payment

import kotlinx.serialization.Serializable

@Serializable
data class PaymentMethodResponse(
    val id: String,
    val brand: String?,
    val last4: String?,
    val expMonth: Int?,
    val expYear: Int?
)