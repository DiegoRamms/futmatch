package com.devapplab.model.payment

import kotlinx.serialization.Serializable

@Serializable
data class PaymentHistoryItem(
    val id: String,
    val amount: Long,
    val currency: String,
    val status: String,
    val createdAt: Long,
    val paidAt: Long,
    val paymentMethod: PaymentMethodInfo?,
    val refund: RefundInfo?
)

@Serializable
data class PaymentMethodInfo(
    val last4: String,
    val brand: String
)

@Serializable
data class RefundInfo(
    val id: String,
    val amount: Long,
    val status: String,
    val createdAt: Long,
    val refundedAt: Long
)
