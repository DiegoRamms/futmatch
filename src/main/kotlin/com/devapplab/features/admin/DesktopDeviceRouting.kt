package com.devapplab.features.admin

import com.devapplab.config.getIdentifier
import com.devapplab.config.requireRole
import com.devapplab.model.auth.ClaimType
import com.devapplab.features.device.DesktopDeviceController
import com.devapplab.model.user.UserRole
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.plugin.scope
import java.util.UUID

fun Route.desktopDeviceAdminRouting() {
    route("/admin/desktop-devices") {
        post("/approve-enrollment") {
            val adminId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
            call.requireRole(UserRole.ADMIN)
            call.scope.get<DesktopDeviceController>().approveFromMobile(call, adminId)
        }
        delete("/{deviceId}") {
            call.requireRole(UserRole.ADMIN)
            val deviceId = UUID.fromString(call.parameters["deviceId"])
            call.scope.get<DesktopDeviceController>().revoke(call, deviceId)
        }
    }
}
