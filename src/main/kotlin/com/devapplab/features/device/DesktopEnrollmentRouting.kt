package com.devapplab.features.device

import com.devapplab.config.RateLimitType
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.plugin.scope

/** Public before login, but protected by proof of possession of the submitted P-256 key. */
fun Route.desktopEnrollmentRouting() {
    rateLimit(RateLimitName(RateLimitType.DESKTOP_ENROLLMENT_CREATE.value)) {
        route("/desktop/enrollments") {
            post {
                call.scope.get<DesktopDeviceController>().createEnrollment(call)
            }
        }
    }
}
