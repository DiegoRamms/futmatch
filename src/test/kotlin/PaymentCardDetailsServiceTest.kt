import com.devapplab.data.repository.payment.AdminUserPaymentHistoryItem
import com.devapplab.data.repository.payment.PaymentRepository
import com.devapplab.model.payment.PaymentAttemptStatus
import com.devapplab.model.user.response.AdminUserPaymentMethodResponse
import com.devapplab.service.payment.PaymentCardDetailsService
import com.devapplab.service.payment.StripePaymentCardReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.lang.reflect.Proxy
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.*

class PaymentCardDetailsServiceTest {
    private val card = AdminUserPaymentMethodResponse("visa", "4242")
    private fun item() = AdminUserPaymentHistoryItem(
        UUID.randomUUID(), "Test field", 100L, BigDecimal("120.00"), "MXN",
        PaymentAttemptStatus.SUCCEEDED, 200L, 200L, null, null, null,
        providerPaymentId = "pi_test"
    )
    private fun repository(save: (String, String, String) -> Boolean): PaymentRepository =
        Proxy.newProxyInstance(PaymentRepository::class.java.classLoader, arrayOf(PaymentRepository::class.java)) { _, method, args ->
            check(method.name == "updatePaymentCardDetails") { "Unexpected repository call: ${method.name}" }
            save(args[0] as String, args[1] as String, args[2] as String)
        } as PaymentRepository

    @Test fun cachedCardSkipsStripeAndPersistence() = runBlocking {
        val service = PaymentCardDetailsService(repository { _, _, _ -> error("Must not write") }, StripePaymentCardReader { error("Must not query") })
        assertEquals(card, service.resolve(item().copy(cardBrand = "visa", cardLast4 = "4242")))
    }

    @Test fun missingCardIsRecoveredAndReused() = runBlocking {
        var stored: AdminUserPaymentMethodResponse? = null
        var calls = 0
        val service = PaymentCardDetailsService(repository { id, brand, last4 ->
            assertEquals("pi_test", id)
            stored = AdminUserPaymentMethodResponse(brand, last4)
            true
        }, StripePaymentCardReader { calls++; card })
        assertEquals(card, service.resolve(item()))
        assertEquals(card, stored)
        assertEquals(card, service.resolve(item().copy(cardBrand = stored?.brand, cardLast4 = stored?.last4)))
        assertEquals(1, calls)
    }

    @Test fun stripeFailureReturnsMissingCard() = runBlocking {
        val service = PaymentCardDetailsService(repository { _, _, _ -> error("Must not write") }, StripePaymentCardReader { throw IllegalStateException("Provider unavailable") })
        assertNull(service.resolve(item()))
    }

    @Test fun persistenceFailureStillReturnsRecoveredCard() = runBlocking {
        val service = PaymentCardDetailsService(repository { _, _, _ -> throw IllegalStateException("DB unavailable") }, StripePaymentCardReader { card })
        assertEquals(card, service.resolve(item()))
    }

    @Test fun absentOrInvalidCardIsNotSaved() = runBlocking {
        for (value in listOf(null, AdminUserPaymentMethodResponse("visa", "4242424242424242"), AdminUserPaymentMethodResponse("", "4242"))) {
            val service = PaymentCardDetailsService(repository { _, _, _ -> error("Must not write") }, StripePaymentCardReader { value })
            assertNull(service.resolve(item()))
        }
    }

    @Test fun missingProviderIdSkipsStripe() = runBlocking {
        val service = PaymentCardDetailsService(repository { _, _, _ -> error("Must not write") }, StripePaymentCardReader { error("Must not query") })
        assertNull(service.resolve(item().copy(providerPaymentId = null)))
    }

    @Test fun cancellationPropagates() = runBlocking {
        val service = PaymentCardDetailsService(repository { _, _, _ -> error("Must not write") }, StripePaymentCardReader { throw CancellationException() })
        assertFailsWith<CancellationException> { service.resolve(item()) }
        Unit
    }
}
