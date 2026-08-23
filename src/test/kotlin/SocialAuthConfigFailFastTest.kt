package com.devapplab

import com.devapplab.di.configModule
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.MapApplicationConfig
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A missing social-auth environment variable must bring the application down at startup,
 * not at the first request that happens to touch the dependency chain.
 *
 * These configs are only reachable through `AuthController`, which every route under
 * `/auth` resolves per request. While they were lazy, deploying without the
 * `APPLE_SIGN_IN` variables produced a healthy-looking instance that answered 500 on
 * unrelated endpoints such as `POST /auth/refresh`. Declaring them
 * `createdAtStart = true` turns that into a boot failure, so a misconfigured deploy
 * never takes traffic.
 */
class SocialAuthConfigFailFastTest {

    @Test
    fun `boots when every social auth variable is present`() {
        startEagerly(configOf(*BASE_ENTRIES, *APPLE_ENTRIES))
    }

    @Test
    fun `fails at startup when the apple client id is missing`() {
        assertStartupFailureNames(
            variable = "APPLE_SIGN_IN_CLIENT_ID",
            config = configOf(*BASE_ENTRIES, *APPLE_ENTRIES.without("appleAuth.clientId"))
        )
    }

    @Test
    fun `fails at startup when the apple private key is missing`() {
        assertStartupFailureNames(
            variable = "APPLE_SIGN_IN_PRIVATE_KEY_BASE64",
            config = configOf(*BASE_ENTRIES, *APPLE_ENTRIES.without("appleAuth.privateKeyBase64"))
        )
    }

    @Test
    fun `fails at startup when the google web client id is missing`() {
        assertStartupFailureNames(
            variable = "GOOGLE_OAUTH_WEB_CLIENT_ID",
            config = configOf(*BASE_ENTRIES.without("googleAuth.webClientId"), *APPLE_ENTRIES)
        )
    }

    private fun startEagerly(config: ApplicationConfig) {
        val app = koinApplication(createEagerInstances = false) {
            modules(
                module { single { config } },
                configModule
            )
        }
        try {
            app.createEagerInstances()
        } finally {
            // `configModule` is a shared top-level val, so its SingleInstanceFactory keeps
            // whatever a previous test created. Closing drops those cached instances and
            // lets the next case re-run the validation instead of silently reusing them.
            app.close()
        }
    }

    private fun assertStartupFailureNames(variable: String, config: ApplicationConfig) {
        val error = assertFailsWith<Throwable> { startEagerly(config) }

        assertTrue(
            generateSequence(error) { it.cause }.any { it.message?.contains(variable) == true },
            "expected the startup failure to name $variable, got: ${error.message}"
        )
    }

    private fun configOf(vararg entries: Pair<String, String>) = MapApplicationConfig(*entries)

    private fun Array<Pair<String, String>>.without(key: String) =
        filterNot { it.first == key }.toTypedArray()

    private companion object {
        // App Check is switched off so these cases exercise the social-auth configs alone.
        val BASE_ENTRIES = arrayOf(
            "googleAuth.webClientId" to "google-web-client-id",
            "appCheck.enabled" to "false",
            "appCheck.enforce" to "false"
        )

        val APPLE_ENTRIES = arrayOf(
            "appleAuth.clientId" to "com.futmatch.client",
            "appleAuth.teamId" to "TEAMID1234",
            "appleAuth.keyId" to "KEYID12345",
            "appleAuth.privateKeyBase64" to "cHJpdmF0ZS1rZXk="
        )
    }
}
