package com.devapplab.service.payment

import com.devapplab.model.payment.PaymentAttemptStatus
import com.devapplab.model.payment.PaymentCaptureMethod
import com.devapplab.model.payment.PaymentOperationResult
import com.devapplab.model.payment.PaymentProvider
import kotlinx.serialization.Serializable

interface PaymentService {
    suspend fun createPaymentIntent(
        amount: Long,
        currency: String,
        metadata: Map<String, String>,
        captureMethod: PaymentCaptureMethod,
        customerId: String?
    ): PaymentOperationResult

    suspend fun confirmPayment(
        paymentId: String
    ): PaymentAttemptStatus

    /**
     * Captures a pre-authorized payment.
     * Returns true if capture was successful, false otherwise.
     * Errors are logged internally.
     */
    suspend fun capturePayment(
        paymentId: String,
        amount: Long
    ): Boolean

    /**
     * Cancels a payment intent (releases the hold).
     * Returns true if cancellation was successful.
     */
    suspend fun cancelPayment(
        paymentId: String
    ): Boolean
}

@Serializable
data class PaymentIntentResult(
    val clientSecret: String,
    val paymentId: String,
    val provider: PaymentProvider,
    val customer: String?,
    val customerSessionClientSecret: String?,
    val publishableKey: String?
)
