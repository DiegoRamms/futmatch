package com.devapplab.model.match.request

import com.devapplab.model.match.TeamType
import com.devapplab.model.payment.PaymentProvider
import kotlinx.serialization.Serializable

@Serializable
data class JoinMatchRequest(
    val team: TeamType? = null,
    val paymentProvider: PaymentProvider = PaymentProvider.STRIPE
)
