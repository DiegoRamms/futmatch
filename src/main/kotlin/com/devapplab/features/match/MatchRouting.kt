package com.devapplab.features.match

import com.devapplab.config.requireRole
import com.devapplab.model.user.UserRole
import io.ktor.server.routing.*
import org.koin.ktor.plugin.scope

fun Route.matchRouting() {

    route("match") {
        post("admin/create") {
            call.requireRole(UserRole.ADMIN, UserRole.BOTH)
            val matchController = call.scope.get<MatchController>()
            matchController.createMatch(call)
        }
        get("admin/matches/{fieldId}") {
            call.requireRole(UserRole.ADMIN, UserRole.BOTH)
            val matchController = call.scope.get<MatchController>()
            matchController.getMatchesByFieldId(call)
        }
        patch("admin/cancel/{matchId}") {
            call.requireRole(UserRole.ADMIN, UserRole.BOTH)
            val matchController = call.scope.get<MatchController>()
            matchController.cancelMatch(call)
        }
        put("admin/update/{matchId}") {
            call.requireRole(UserRole.ADMIN, UserRole.BOTH)
            val matchController = call.scope.get<MatchController>()
            matchController.updateMatch(call)
        }
    }
}