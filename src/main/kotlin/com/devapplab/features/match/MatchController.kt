package com.devapplab.features.match

import com.devapplab.config.getIdentifier
import com.devapplab.model.auth.ClaimType
import com.devapplab.model.match.mapper.toMatch
import com.devapplab.model.match.request.CreateMatchRequest
import com.devapplab.utils.respond
import com.devapplab.utils.toUUIDOrNull
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*

class MatchController(private val matchService: com.devapplab.service.match.MatchService) {
    suspend fun createMatch(call: ApplicationCall) {
        val adminId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val request = call.receive<CreateMatchRequest>()
        val result = matchService.create(request.toMatch(adminId))
        call.respond(result)
    }

    suspend fun getMatchesByFieldId(call: ApplicationCall) {
        val fieldId = call.parameters["fieldId"]?.toUUIDOrNull() ?: throw NotFoundException("Can't find field id")
        val result = matchService.getMatchesByFieldId(fieldId = fieldId)
        call.respond(result)
    }

    suspend fun cancelMatch(call: ApplicationCall) {
        val matchId = call.parameters["matchId"]?.toUUIDOrNull() ?: throw NotFoundException("Can't cancel match")
        val result = matchService.cancelMatch(matchId)
        call.respond(result)
    }
}