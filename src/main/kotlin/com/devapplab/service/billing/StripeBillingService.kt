package com.devapplab.service.billing


import com.devapplab.data.repository.user.UserRepository
import com.devapplab.model.StripeConfig
import com.devapplab.model.payment.PaymentProvider
import com.stripe.Stripe
import com.stripe.model.Customer
import com.stripe.model.CustomerSession
import com.stripe.model.PaymentMethod
import com.stripe.model.SetupIntent
import com.stripe.param.CustomerCreateParams
import com.stripe.param.CustomerSessionCreateParams
import com.stripe.param.PaymentMethodListParams
import com.stripe.param.SetupIntentCreateParams
import java.util.*

class StripeBillingService(
    private val stripeConfig: StripeConfig,
    private val userRepository: UserRepository
) : BillingService {

    init {
        Stripe.apiKey = stripeConfig.apiKey
    }

    override fun getPublishableKey(): String = stripeConfig.publishableKey

    override suspend fun getOrCreateCustomer(userId: UUID, provider: PaymentProvider): String {
        require(provider == PaymentProvider.STRIPE) {
            "StripeBillingService only supports STRIPE provider. provider=$provider"
        }

        val existing = userRepository.getPaymentProfile(userId, provider)
        if (!existing.isNullOrBlank()) return existing

        val params = CustomerCreateParams.builder()
            .putMetadata("appUserId", userId.toString())
            .build()

        val customer = Customer.create(params)
        userRepository.upsertPaymentProfile(userId, provider, customer.id)
        return customer.id
    }

    override suspend fun createCustomerSession(customerId: String): String {
        require(customerId.isNotBlank()) { "customerId cannot be blank" }

        val params = CustomerSessionCreateParams.builder()
            .setCustomer(customerId)
            .setComponents(
                CustomerSessionCreateParams.Components.builder()
                    .setPaymentElement(
                        CustomerSessionCreateParams.Components.PaymentElement.builder()
                            .setEnabled(true)
                            .setFeatures(
                                CustomerSessionCreateParams.Components.PaymentElement.Features.builder()
                                    .setPaymentMethodRedisplay(
                                        CustomerSessionCreateParams.Components.PaymentElement.Features.PaymentMethodRedisplay.ENABLED
                                    )
                                    .setPaymentMethodSave(
                                        CustomerSessionCreateParams.Components.PaymentElement.Features.PaymentMethodSave.ENABLED
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        val session = CustomerSession.create(params)
        return session.clientSecret ?: throw IllegalStateException("CustomerSession client_secret was null")
    }

    override suspend fun createSetupIntent(customerId: String): String {
        require(customerId.isNotBlank()) { "customerId cannot be blank" }

        val params = SetupIntentCreateParams.builder()
            .setCustomer(customerId)
            .build()

        val setup = SetupIntent.create(params)
        return setup.clientSecret ?: throw IllegalStateException("SetupIntent client_secret was null")
    }

    override suspend fun listCardPaymentMethods(customerId: String): List<CardPaymentMethod> {
        require(customerId.isNotBlank()) { "customerId cannot be blank" }

        val params = PaymentMethodListParams.builder()
            .setCustomer(customerId)
            .setType(PaymentMethodListParams.Type.CARD)
            .build()

        val list = PaymentMethod.list(params)

        return list.data.map { pm ->
            val card = pm.card
            CardPaymentMethod(
                id = pm.id,
                brand = card?.brand,
                last4 = card?.last4,
                expMonth = card?.expMonth?.toInt(),
                expYear = card?.expYear?.toInt()
            )
        }
    }

    override suspend fun detachPaymentMethod(paymentMethodId: String): Boolean {
        require(paymentMethodId.isNotBlank()) { "paymentMethodId cannot be blank" }

        val pm = PaymentMethod.retrieve(paymentMethodId)
        val detached = pm.detach()
        return detached.customer == null
    }
}