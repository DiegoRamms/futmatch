package com.devapplab.features.admin

import com.devapplab.config.requireRole
import com.devapplab.model.user.UserRole
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.koin.ktor.plugin.scope

fun Route.adminDashboardRouting() {
    route("/admin/desktop/dashboard") {
        get("/summary") {
            call.requireRole(UserRole.ADMIN)
            call.scope.get<AdminDashboardController>().getSummary(call)
        }
    }
}
