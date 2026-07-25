package com.devapplab.features.admin

import com.devapplab.config.getIdentifier
import com.devapplab.config.requireRole
import com.devapplab.model.auth.ClaimType
import com.devapplab.features.device.DesktopDeviceController
import com.devapplab.model.user.UserRole
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.plugin.scope
import java.util.UUID

fun Route.desktopDeviceAdminRouting() {
    route("/admin/desktop-enrollment-requests") {
        post {
            val adminId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
            call.requireRole(UserRole.ADMIN)
            call.scope.get<DesktopDeviceController>().createEnrollment(call, adminId)
        }
        get {
            call.requireRole(UserRole.ADMIN)
            call.scope.get<DesktopDeviceController>().pendingEnrollments(call)
        }
        post("/{deviceId}/approve") {
            val adminId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
            call.requireRole(UserRole.ADMIN)
            call.scope.get<DesktopDeviceController>().approve(call, adminId, UUID.fromString(call.parameters["deviceId"]))
        }
        post("/{deviceId}/reject") {
            val adminId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
            call.requireRole(UserRole.ADMIN)
            call.scope.get<DesktopDeviceController>().reject(call, adminId, UUID.fromString(call.parameters["deviceId"]))
        }
    }
    route("/admin/desktop-devices") {
        delete("/{deviceId}") {
            call.requireRole(UserRole.ADMIN)
            val deviceId = UUID.fromString(call.parameters["deviceId"])
            call.scope.get<DesktopDeviceController>().revoke(call, deviceId)
        }
    }
}
