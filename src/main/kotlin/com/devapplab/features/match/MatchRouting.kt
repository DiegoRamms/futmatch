package com.devapplab.features.match

import com.devapplab.config.requireRole
import com.devapplab.model.user.UserRole
import io.ktor.http.ContentType
import io.ktor.server.routing.*
import org.koin.ktor.plugin.scope

fun Route.matchRouting() {

    route("match") {
        post("admin/create") {
            call.requireRole(UserRole.ADMIN, UserRole.ORGANIZER)
            val matchController = call.scope.get<MatchController>()
            matchController.createMatch(call)
        }
        get("admin/matches/{fieldId}") {
            call.requireRole(UserRole.ADMIN, UserRole.ORGANIZER)
            val matchController = call.scope.get<MatchController>()
            matchController.getMatchesByFieldId(call)
        }
        get("admin/matches") {
            call.requireRole(UserRole.ADMIN, UserRole.ORGANIZER)
            val matchController = call.scope.get<MatchController>()
            matchController.getAllMatches(call)
        }
        patch("admin/cancel/{matchId}") {
            call.requireRole(UserRole.ADMIN, UserRole.ORGANIZER)
            val matchController = call.scope.get<MatchController>()
            matchController.cancelMatch(call)
        }
        get("admin/failed-refunds") {
            call.requireRole(UserRole.ADMIN)
            val matchController = call.scope.get<MatchController>()
            matchController.getFailedRefunds(call)
        }
        post("admin/failed-refunds/{failureId}/retry") {
            call.requireRole(UserRole.ADMIN)
            val matchController = call.scope.get<MatchController>()
            matchController.retryFailedRefund(call)
        }
        post("admin/failed-refunds/{failureId}/resolve") {
            call.requireRole(UserRole.ADMIN)
            val matchController = call.scope.get<MatchController>()
            matchController.resolveFailedRefundManually(call)
        }
        post("admin/{matchId}/complete") {
            call.requireRole(UserRole.ADMIN, UserRole.ORGANIZER)
            val matchController = call.scope.get<MatchController>()
            matchController.completeMatch(call)
        }
        put("admin/update/{matchId}") {
            call.requireRole(UserRole.ADMIN, UserRole.ORGANIZER)
            val matchController = call.scope.get<MatchController>()
            matchController.updateMatch(call)
        }

        get("matches") {
            val matchController = call.scope.get<MatchController>()
            matchController.getPlayerMatches(call)
        }

        get("my-matches") {
            call.requireRole(UserRole.PLAYER, UserRole.ADMIN, UserRole.ORGANIZER)
            val matchController = call.scope.get<MatchController>()
            matchController.getUserMatches(call)
        }

        get("/{matchId}") {
            val matchController = call.scope.get<MatchController>()
            matchController.getMatchDetail(call)
        }

        accept(ContentType.Text.EventStream){
            get("/{matchId}/stream") {
                val matchController = call.scope.get<MatchController>()
                matchController.streamMatchDetail(call)
            }
        }

        post("{matchId}/join") {
            call.requireRole(UserRole.PLAYER, UserRole.ADMIN, UserRole.ORGANIZER)
            val matchController = call.scope.get<MatchController>()
            matchController.joinMatch(call)
        }

        post("{matchId}/leave") {
            call.requireRole(UserRole.PLAYER, UserRole.ADMIN, UserRole.ORGANIZER)
            val matchController = call.scope.get<MatchController>()
            matchController.leaveMatch(call)
        }
    }
}