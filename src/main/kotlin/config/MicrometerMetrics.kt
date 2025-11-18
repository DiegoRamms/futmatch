package com.devapplab.config

import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry


private val prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

fun Application.configureMicrometerMetrics() {


    install(MicrometerMetrics) {
        registry = prometheusRegistry
    }

    routing {
        get("/metrics") {
            //TODO Add evn token for prod
            call.respond(prometheusRegistry.scrape())
        }
    }
}