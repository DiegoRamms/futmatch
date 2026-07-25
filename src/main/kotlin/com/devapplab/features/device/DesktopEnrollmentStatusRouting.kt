package com.devapplab.features.device

import com.devapplab.config.RateLimitType
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.routing.*
import org.koin.ktor.plugin.scope

/** Public only in the sense that it precedes login; it requires the approved device's signature. */
fun Route.desktopEnrollmentStatusRouting() {
    rateLimit(RateLimitName(RateLimitType.DESKTOP_ENROLLMENT_STATUS.value)) {
        route("/desktop/enrollment-status") {
            get("/{deviceId}") {
                call.scope.get<DesktopDeviceController>().enrollmentStatus(call)
            }
        }
    }
}
