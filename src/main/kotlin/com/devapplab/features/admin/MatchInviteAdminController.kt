package com.devapplab.features.admin

import com.devapplab.model.AppResult
import com.devapplab.service.match.MatchInviteService
import com.devapplab.utils.respond
import com.devapplab.utils.retrieveLocale
import com.devapplab.utils.toUUIDOrNull
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException

class MatchInviteAdminController(private val service: MatchInviteService) {

    suspend fun diagnose(call: ApplicationCall) {
        val matchId = call.request.queryParameters["matchId"]?.toUUIDOrNull()
            ?: throw BadRequestException("matchId query parameter must be a UUID")
        val userId = call.request.queryParameters["userId"]?.toUUIDOrNull()
            ?: throw BadRequestException("userId query parameter must be a UUID")
        call.respond(service.diagnose(matchId, userId, call.retrieveLocale()))
    }

    suspend fun run(call: ApplicationCall) {
        call.respond(AppResult.Success(service.sendMatchInvites()))
    }
}
