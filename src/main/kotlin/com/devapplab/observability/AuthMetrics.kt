package com.devapplab.observability

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry

/** Bounded auth metrics that are safe to expose to Prometheus. */
class AuthMetrics(
    private val meterRegistry: MeterRegistry
) {
    fun recordRefreshRejected(reason: RefreshRejectionReason) {
        Counter.builder("auth.refresh.rejected")
            .description("Rejected refresh-token requests")
            .tag("reason", reason.metricValue)
            .register(meterRegistry)
            .increment()
    }
}

enum class RefreshRejectionReason(val metricValue: String) {
    MISSING_TOKEN("missing_token"),
    UNKNOWN_TOKEN("unknown_token"),
    EXPIRED_TOKEN("expired_token"),
    REVOKED_TOKEN("revoked_token"),
    REUSE_DETECTED("reuse_detected"),
    ROTATION_CONFLICT("rotation_conflict")
}
