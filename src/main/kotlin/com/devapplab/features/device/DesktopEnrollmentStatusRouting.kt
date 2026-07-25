package com.devapplab.features.device

import com.devapplab.config.RateLimitType
import com.devapplab.service.device.DesktopDeviceSecurityService
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import org.koin.ktor.plugin.scope

/** Public only in the sense that it precedes login; it requires the pending device's signature. */
fun Route.desktopEnrollmentStatusRouting(desktopDeviceSecurityService: DesktopDeviceSecurityService) {
    rateLimit(RateLimitName(RateLimitType.DESKTOP_ENROLLMENT_STATUS.value)) {
        route("/desktop/enrollment-status") {
            get("/{deviceId}") {
                val status = desktopDeviceSecurityService.enrollmentStatus(
                    deviceId = call.parameters["deviceId"],
                    timestamp = call.request.header("X-Desktop-Timestamp"),
                    requestId = call.request.header("X-Desktop-Request-Id"),
                    signature = call.request.header("X-Desktop-Signature"),
                    method = call.request.httpMethod.value,
                    path = call.request.path()
                )
                call.scope.get<DesktopDeviceController>().enrollmentStatus(call, status)
            }
        }
    }
}
