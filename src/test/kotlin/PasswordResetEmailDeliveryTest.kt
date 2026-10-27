import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.devapplab.data.database.executor.DbExecutor
import com.devapplab.data.repository.MfaCodeRepository
import com.devapplab.data.repository.user.UserRepository
import com.devapplab.model.AppResult
import com.devapplab.model.auth.UserSignInInfo
import com.devapplab.model.auth.request.ForgotPasswordRequest
import com.devapplab.model.mfa.*
import com.devapplab.model.user.*
import com.devapplab.observability.AuthRequestContext
import com.devapplab.service.auth.PasswordResetService
import com.devapplab.service.auth.mfa.MfaCodeService
import com.devapplab.service.auth.mfa.MfaRateLimitConfig
import com.devapplab.service.email.EmailService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.lang.reflect.Proxy
import java.util.Locale
import java.util.UUID
import kotlin.test.*

class PasswordResetEmailDeliveryTest {
    @Test
    fun `failed delivery logs failure and keeps verification attempts`() = runBlocking {
        val fixture = Fixture(delivery = { false })
        fixture.withLogs { logs ->
            assertIs<AppResult.Success<*>>(fixture.request())
            assertEquals(1, fixture.sent)
            assertEquals(0, fixture.cleaned)
            assertTrue(logs.any { it.contains("email_send_failed") })
            assertFalse(logs.any { it.contains("auth.password_reset.request.success") })
        }
    }

    @Test
    fun `thrown delivery failure keeps generic response and logs failure`() = runBlocking {
        val fixture = Fixture(delivery = { error("Provider unavailable") })
        fixture.withLogs { logs ->
            assertIs<AppResult.Success<*>>(fixture.request())
            assertEquals(0, fixture.cleaned)
            assertTrue(logs.any { it.contains("email_send_failed") })
            assertFalse(logs.any { it.contains("auth.password_reset.request.success") })
        }
    }

    @Test
    fun `accepted delivery logs success and clears verification attempts`() = runBlocking {
        val fixture = Fixture(delivery = { true })
        fixture.withLogs { logs ->
            assertIs<AppResult.Success<*>>(fixture.request())
            assertEquals(1, fixture.sent)
            assertEquals(1, fixture.cleaned)
            assertTrue(logs.any { it.contains("auth.password_reset.request.success") })
            assertFalse(logs.any { it.contains("email_send_failed") })
        }
    }

    @Test
    fun `cooldown skips delivery and logs remaining wait`() = runBlocking {
        val fixture = Fixture(delivery = { true }, cooldown = true)
        fixture.withLogs { logs ->
            assertIs<AppResult.Success<*>>(fixture.request())
            assertEquals(0, fixture.sent)
            assertEquals(0, fixture.cleaned)
            assertTrue(logs.any { it.contains("cooldown") && it.contains("retryAfterSeconds") })
        }
    }

    private class Fixture(delivery: () -> Boolean, cooldown: Boolean = false) {
        var sent = 0
        var cleaned = 0
        private val userId = UUID.randomUUID()
        private val user = UserBaseInfo(
            userId, "Test", "User", "user@gmail.com", "1234567890", UserStatus.ACTIVE,
            "MX", 0, Gender.OTHER, PlayerPosition.FORWARD, null, PlayerLevel.ANY,
            UserRole.PLAYER, true, 0, 0
        )
        private val service = PasswordResetService(
            dbExecutor = object : DbExecutor {
                override suspend fun <T> tx(block: () -> T): T = block()
            },
            userRepository = stub<UserRepository> { method ->
                when (method) {
                    "findByEmail" -> user
                    "getUserSignInInfoById" -> UserSignInInfo(userId, UserRole.PLAYER, "hash", UserStatus.ACTIVE, true)
                    else -> error("Unexpected call: $method")
                }
            },
            hashingService = stub { "hashed-code" },
            passwordResetTokenService = stub(),
            passwordResetTokenRepository = stub(),
            mfaCodeService = MfaCodeService(stub<MfaCodeRepository> { method ->
                when (method) {
                    "findLatestMfaCode" -> if (cooldown) MfaData(
                        UUID.randomUUID(), userId, null, "hash", MfaChannel.EMAIL,
                        MfaPurpose.PASSWORD_RESET, System.currentTimeMillis() + 300_000,
                        false, null, true, System.currentTimeMillis()
                    ) else null
                    "countRecentCodes" -> 0L
                    "createMfaCode" -> UUID.randomUUID()
                    else -> error("Unexpected call: $method")
                }
            }),
            emailService = stub<EmailService> { method ->
                check(method == "sendMfaPasswordResetEmail")
                sent++
                delivery()
            },
            authRepository = stub(),
            refreshTokenRepository = stub(),
            loginAttemptRepository = stub(),
            passwordResetVerifyAttemptRepository = stub { method ->
                check(method == "deleteSafe")
                cleaned++
                true
            },
            mfaRateLimitConfig = MfaRateLimitConfig(60, 5, 1, 10)
        )

        suspend fun request() = service.forgotPassword(
            Locale.US, ForgotPasswordRequest(user.email),
            AuthRequestContext("test-request", "POST", "/auth/forgot-password")
        )

        suspend fun withLogs(block: suspend (List<String>) -> Unit) {
            val logger = LoggerFactory.getLogger(PasswordResetService::class.java) as Logger
            val appender = ListAppender<ILoggingEvent>().apply { start() }
            logger.addAppender(appender)
            // Read messages lazily so assertions see events emitted during the request.
            val messages = object : AbstractList<String>() {
                override val size get() = appender.list.size
                override fun get(index: Int) = appender.list[index].formattedMessage
            }
            try {
                block(messages)
            } finally {
                logger.detachAppender(appender)
                appender.stop()
            }
        }
    }
}

private inline fun <reified T> stub(crossinline handler: (String) -> Any? = { error("Unexpected call: $it") }): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        handler(method.name)
    } as T
