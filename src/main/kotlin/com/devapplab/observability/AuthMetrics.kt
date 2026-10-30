package com.devapplab.observability

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry

/** Bounded auth metrics that are safe to expose to Prometheus. */
class AuthMetrics(
    private val meterRegistry: MeterRegistry
) {
    init {
        RefreshRejectionReason.entries.forEach { refreshCounter(it) }
        SecurityAuthEvent.entries.forEach { securityCounter(it) }
    }

    fun recordRefreshRejected(reason: RefreshRejectionReason) {
        refreshCounter(reason).increment()
    }

    fun recordSecurityEvent(event: SecurityAuthEvent) {
        securityCounter(event).increment()
    }

    private fun refreshCounter(reason: RefreshRejectionReason): Counter =
        Counter.builder("auth.refresh.rejected")
            .description("Rejected refresh-token requests")
            .tag("reason", reason.metricValue)
            .register(meterRegistry)

    private fun securityCounter(event: SecurityAuthEvent): Counter =
        Counter.builder("auth.security.events")
            .description("Authentication security events")
            .tag("event", event.metricValue)
            .register(meterRegistry)
}

enum class SecurityAuthEvent(val metricValue: String) {
    INVALID_CREDENTIALS("invalid_credentials"),
    LOGIN_LOCKED("login_locked"),
    MFA_REJECTED("mfa_rejected"),
    APP_CHECK_MISSING("app_check_missing"),
    APP_CHECK_INVALID("app_check_invalid"),
    ADMIN_ROLE_CHANGED("admin_role_changed"),
    DESKTOP_ENROLLMENT_APPROVED("desktop_enrollment_approved")
}

enum class RefreshRejectionReason(val metricValue: String) {
    MISSING_TOKEN("missing_token"),
    UNKNOWN_TOKEN("unknown_token"),
    EXPIRED_TOKEN("expired_token"),
    REVOKED_TOKEN("revoked_token"),
    REUSE_DETECTED("reuse_detected"),
    ROTATION_CONFLICT("rotation_conflict")
}
