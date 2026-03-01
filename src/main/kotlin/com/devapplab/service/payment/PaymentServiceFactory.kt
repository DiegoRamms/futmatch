package com.devapplab.service.payment

import com.devapplab.model.payment.PaymentProvider

class PaymentServiceFactory(
    private val stripeService: StripePaymentService
) {
    fun getService(provider: PaymentProvider): PaymentService {
        return when (provider) {
            PaymentProvider.STRIPE -> stripeService
            else -> throw IllegalArgumentException("Unsupported payment provider: $provider")
        }
    }
}
