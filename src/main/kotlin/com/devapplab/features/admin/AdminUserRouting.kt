package com.devapplab.features.admin

import com.devapplab.config.requireRole
import com.devapplab.model.user.UserRole
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.route
import org.koin.ktor.plugin.scope

fun Route.adminUserRouting() {
    route("/admin/users") {
        get {
            call.requireRole(UserRole.ADMIN)
            call.scope.get<AdminUserController>().getManagedUsers(call)
        }

        patch("/{userId}/access") {
            call.requireRole(UserRole.ADMIN)
            call.scope.get<AdminUserController>().updateManagedUserAccess(call)
        }
    }
}
