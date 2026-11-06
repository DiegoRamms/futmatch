package com.devapplab.observability

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.time.Duration

/** Measures elapsed time, including connection waits, for each Home operation. */
class HomeMetrics(private val registry: MeterRegistry) {
    private val timers = HomeSection.entries.associateWith { section ->
        Timer.builder("user.home.section.duration")
            .description("Elapsed time for a section of the authenticated home response")
            .tag("section", section.metricValue)
            .publishPercentileHistogram()
            .minimumExpectedValue(Duration.ofMillis(1))
            .maximumExpectedValue(Duration.ofSeconds(30))
            .serviceLevelObjectives(
                Duration.ofMillis(100), Duration.ofMillis(500), Duration.ofSeconds(1),
                Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofSeconds(10)
            )
            .register(registry)
    }

    suspend fun <T> measure(section: HomeSection, block: suspend () -> T): T {
        val sample = Timer.start(registry)
        try {
            return block()
        } finally {
            sample.stop(timers.getValue(section))
        }
    }
}

enum class HomeSection(val metricValue: String) {
    USER("user"),
    SUGGESTED_MATCHES("suggested_matches"),
    LAST_MATCH("last_match"),
    WIN_STATS("win_stats"),
    RANKING("ranking")
}
