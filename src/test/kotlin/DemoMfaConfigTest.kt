import com.devapplab.di.configModule
import com.devapplab.service.auth.mfa.DemoMfaConfig
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.MapApplicationConfig
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DemoMfaConfigTest {

    @Test
    fun `shortcut is disabled when the variables are absent`() {
        val demo = load(configOf(*BASE_ENTRIES))

        assertFalse(demo.enabled)
        assertFalse(demo.isDemoUser("demo@futmatch.mx"))
        assertFalse(demo.matches("123456"))
    }

    @Test
    fun `shortcut is disabled when enabled is false even if email and code are set`() {
        val demo = load(configOf(*BASE_ENTRIES, "demoMfa.enabled" to "false", *DEMO_ENTRIES))

        assertFalse(demo.isDemoUser("demo@futmatch.mx"))
        assertFalse(demo.matches("123456"))
    }

    @Test
    fun `enabled shortcut only accepts the exact account and code`() {
        val demo = load(configOf(*BASE_ENTRIES, "demoMfa.enabled" to "true", *DEMO_ENTRIES))

        assertTrue(demo.isDemoUser("demo@futmatch.mx"))
        assertTrue(demo.isDemoUser("Demo@Futmatch.mx"))
        assertFalse(demo.isDemoUser("other@futmatch.mx"))
        assertFalse(demo.isDemoUser("demo@futmatch.mx.evil.com"))
        assertTrue(demo.matches("123456"))
        assertFalse(demo.matches("654321"))
        assertFalse(demo.matches(""))
    }

    @Test
    fun `fails at startup when enabled without an email`() {
        assertStartupFailureNames(
            "DEMO_MFA_EMAIL",
            configOf(*BASE_ENTRIES, "demoMfa.enabled" to "true", "demoMfa.code" to "123456")
        )
    }

    @Test
    fun `fails at startup when the code is not six digits`() {
        listOf("", "12345", "1234567", "12ab56").forEach { badCode ->
            assertStartupFailureNames(
                "DEMO_MFA_CODE",
                configOf(
                    *BASE_ENTRIES,
                    "demoMfa.enabled" to "true",
                    "demoMfa.email" to "demo@futmatch.mx",
                    "demoMfa.code" to badCode
                )
            )
        }
    }

    @Test
    fun `default config rejects everything`() {
        val demo = DemoMfaConfig()
        assertEquals(false, demo.enabled)
        assertFalse(demo.isDemoUser(""))
        assertFalse(demo.matches(""))
    }

    private fun load(config: ApplicationConfig): DemoMfaConfig {
        val app = koinApplication(createEagerInstances = false) {
            modules(module { single { config } }, configModule)
        }
        try {
            app.createEagerInstances()
            return app.koin.get()
        } finally {
            app.close()
        }
    }

    private fun assertStartupFailureNames(variable: String, config: ApplicationConfig) {
        val error = assertFailsWith<Throwable> { load(config) }
        assertTrue(
            generateSequence(error) { it.cause }.any { it.message?.contains(variable) == true },
            "expected the startup failure to name $variable, got: ${error.message}"
        )
    }

    private fun configOf(vararg entries: Pair<String, String>) = MapApplicationConfig(*entries)

    private companion object {
        val BASE_ENTRIES = arrayOf(
            "googleAuth.webClientId" to "google-web-client-id",
            "appleAuth.clientId" to "com.futmatch.client",
            "appleAuth.teamId" to "TEAMID1234",
            "appleAuth.keyId" to "KEYID12345",
            "appleAuth.privateKeyBase64" to "cHJpdmF0ZS1rZXk=",
            "appCheck.enabled" to "false",
            "appCheck.enforce" to "false"
        )

        val DEMO_ENTRIES = arrayOf(
            "demoMfa.email" to "demo@futmatch.mx",
            "demoMfa.code" to "123456"
        )
    }
}
