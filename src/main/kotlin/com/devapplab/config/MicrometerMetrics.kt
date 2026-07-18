package com.devapplab.config

import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.http.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import java.security.MessageDigest


private val prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

fun Application.configureMicrometerMetrics() {
    val metricsBearerToken = environment.config.requiredMetricsBearerToken()

    install(MicrometerMetrics) {
        registry = prometheusRegistry
    }

    routing {
        get("/metrics") {
            val providedToken = call.request.headers[HttpHeaders.Authorization]
                ?.takeIf { it.startsWith("Bearer ") }
                ?.removePrefix("Bearer ")
                ?.trim()

            if (providedToken == null || !providedToken.matches(metricsBearerToken)) {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }

            call.respond(prometheusRegistry.scrape())
        }
    }
}

private fun ApplicationConfig.requiredMetricsBearerToken(): String =
    propertyOrNull("metrics.bearer_token")?.getString()?.takeIf(String::isNotBlank)
        ?: error("Missing required metrics configuration: metrics.bearer_token")

private fun String.matches(expected: String): Boolean =
    MessageDigest.isEqual(toByteArray(Charsets.UTF_8), expected.toByteArray(Charsets.UTF_8))
