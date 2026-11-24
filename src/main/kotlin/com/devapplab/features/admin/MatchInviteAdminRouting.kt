package com.devapplab.features.admin

import com.devapplab.config.requireRole
import com.devapplab.model.user.UserRole
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.plugin.scope

fun Route.matchInviteAdminRouting() {
    route("/admin/match-invites") {
        // Explains why a user would or would not get a MATCH_INVITE for a match.
        get("/diagnose") {
            call.requireRole(UserRole.ADMIN)
            call.scope.get<MatchInviteAdminController>().diagnose(call)
        }

        // Runs one invite cycle now instead of waiting for the scheduler.
        post("/run") {
            call.requireRole(UserRole.ADMIN)
            call.scope.get<MatchInviteAdminController>().run(call)
        }
    }
}
