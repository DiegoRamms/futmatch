package com.devapplab.config

import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.http.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.request.path
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import java.security.MessageDigest
import java.time.Duration


private val prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
private val uuidPathSegment = Regex("/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

fun Application.configureMicrometerMetrics() {
    val metricsBearerToken = environment.config.requiredMetricsBearerToken()

    install(MicrometerMetrics) {
        registry = prometheusRegistry
        timers { call, _ ->
            tag("endpoint", call.request.path().normalizeEndpointForMetrics())
        }
        distributionStatisticConfig = DistributionStatisticConfig.builder()
            .percentilesHistogram(true)
            .minimumExpectedValue(Duration.ofMillis(1).toNanos().toDouble())
            .maximumExpectedValue(Duration.ofSeconds(30).toNanos().toDouble())
            .serviceLevelObjectives(
                Duration.ofMillis(100).toNanos().toDouble(),
                Duration.ofMillis(500).toNanos().toDouble(),
                Duration.ofSeconds(1).toNanos().toDouble(),
                Duration.ofSeconds(2).toNanos().toDouble(),
                Duration.ofSeconds(5).toNanos().toDouble()
            )
            .build()
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

fun prometheusMeterRegistry(): PrometheusMeterRegistry = prometheusRegistry

private fun ApplicationConfig.requiredMetricsBearerToken(): String =
    propertyOrNull("metrics.bearer_token")?.getString()?.takeIf(String::isNotBlank)
        ?: error("Missing required metrics configuration: metrics.bearer_token")

private fun String.matches(expected: String): Boolean =
    MessageDigest.isEqual(toByteArray(Charsets.UTF_8), expected.toByteArray(Charsets.UTF_8))

private fun String.normalizeEndpointForMetrics(): String =
    substringBefore('?')
        .replace(uuidPathSegment, "/{id}")
        .ifBlank { "/" }
