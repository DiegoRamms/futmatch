package com.devapplab.service.payment

import com.devapplab.data.repository.payment.AdminUserPaymentHistoryItem
import com.devapplab.data.repository.payment.PaymentRepository
import com.devapplab.model.payment.PaymentProvider
import com.devapplab.model.user.response.AdminUserPaymentMethodResponse
import com.stripe.model.PaymentIntent
import com.stripe.net.RequestOptions
import com.stripe.param.PaymentIntentRetrieveParams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

fun interface StripePaymentCardReader {
    suspend fun read(paymentIntentId: String): AdminUserPaymentMethodResponse?
}

class StripePaymentCardReaderImpl : StripePaymentCardReader {
    override suspend fun read(paymentIntentId: String): AdminUserPaymentMethodResponse? = withContext(Dispatchers.IO) {
        val intent = PaymentIntent.retrieve(
            paymentIntentId,
            PaymentIntentRetrieveParams.builder().addExpand("latest_charge").build(),
            RequestOptions.builder().setConnectTimeout(2_000).setReadTimeout(3_000).setMaxNetworkRetries(0).build()
        )
        val card = intent.latestChargeObject?.paymentMethodDetails?.card
        validPaymentCard(card?.brand, card?.last4)
    }
}

internal fun validPaymentCard(brand: String?, last4: String?): AdminUserPaymentMethodResponse? {
    val normalizedBrand = brand?.trim()?.takeIf { it.isNotEmpty() && it.length <= 32 } ?: return null
    val normalizedLast4 = last4?.trim()?.takeIf { it.matches(Regex("[0-9]{4}")) } ?: return null
    return AdminUserPaymentMethodResponse(normalizedBrand, normalizedLast4)
}

class PaymentCardDetailsService(
    private val repository: PaymentRepository,
    private val reader: StripePaymentCardReader
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun resolve(item: AdminUserPaymentHistoryItem): AdminUserPaymentMethodResponse? {
        validPaymentCard(item.cardBrand, item.cardLast4)?.let { return it }
        if (item.provider != PaymentProvider.STRIPE) return null
        val providerId = item.providerPaymentId?.takeIf { it.startsWith("pi_") } ?: return null
        val card = try {
            reader.read(providerId)?.let { validPaymentCard(it.brand, it.last4) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logger.warn("Payment card lookup failed for paymentId={} type={}", item.id, error.javaClass.simpleName)
            null
        } ?: return null

        try {
            if (!repository.updatePaymentCardDetails(providerId, card.brand, card.last4)) {
                logger.warn("Payment card cache update found no payment for paymentId={}", item.id)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logger.warn("Payment card cache update failed for paymentId={} type={}", item.id, error.javaClass.simpleName)
        }
        return card
    }
}
