package com.devapplab.service.billing

import com.devapplab.model.payment.PaymentProvider
import java.util.UUID


interface BillingService {
    suspend fun getOrCreateCustomer(userId: UUID, provider: PaymentProvider): String

    /** Returns customer_session_client_secret */
    suspend fun createCustomerSession(customerId: String): String

    /** Returns setup_intent client_secret */
    suspend fun createSetupIntent(customerId: String): String

    suspend fun listCardPaymentMethods(customerId: String): List<CardPaymentMethod>

    suspend fun detachPaymentMethod(paymentMethodId: String): Boolean

    fun getPublishableKey(): String
}

data class CardPaymentMethod(
    val id: String,
    val brand: String?,
    val last4: String?,
    val expMonth: Int?,
    val expYear: Int?
)