package com.devapplab.features.match

import com.devapplab.config.getIdentifier
import com.devapplab.model.auth.ClaimType
import com.devapplab.model.match.mapper.toMatch
import com.devapplab.model.match.request.CreateMatchRequest
import com.devapplab.model.match.request.JoinMatchRequest
import com.devapplab.model.match.request.UpdateMatchRequest
import com.devapplab.utils.respond
import com.devapplab.utils.retrieveLocale
import com.devapplab.utils.toUUIDOrNull
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*

class MatchController(private val matchService: com.devapplab.service.match.MatchService) {
    suspend fun createMatch(call: ApplicationCall) {
        val adminId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val request = call.receive<CreateMatchRequest>()
        val locale = call.retrieveLocale()
        val result = matchService.create(request.toMatch(adminId), locale)
        call.respond(result)
    }

    suspend fun getMatchesByFieldId(call: ApplicationCall) {
        val fieldId = call.parameters["fieldId"]?.toUUIDOrNull() ?: throw NotFoundException("Can't find field id")
        val result = matchService.getMatchesByFieldId(fieldId = fieldId)
        call.respond(result)
    }

    suspend fun getAllMatches(call: ApplicationCall) {
        val result = matchService.getAllMatches()
        call.respond(result)
    }

    suspend fun getPlayerMatches(call: ApplicationCall) {
        val lat = call.request.queryParameters["lat"]?.toDoubleOrNull()
        val lon = call.request.queryParameters["lon"]?.toDoubleOrNull()
        val result = matchService.getPlayerMatches(lat, lon)
        call.respond(result)
    }

    suspend fun cancelMatch(call: ApplicationCall) {
        val matchId = call.parameters["matchId"]?.toUUIDOrNull() ?: throw NotFoundException("Can't cancel match")
        val result = matchService.cancelMatch(matchId)
        call.respond(result)
    }

    suspend fun updateMatch(call: ApplicationCall) {
        val matchId = call.parameters["matchId"]?.toUUIDOrNull() ?: throw NotFoundException("Can't update match")
        val adminId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val request = call.receive<UpdateMatchRequest>()
        val result = matchService.updateMatch(matchId, request.toMatch(adminId, matchId))
        call.respond(result)
    }

    suspend fun joinMatch(call: ApplicationCall) {
        val matchId = call.parameters["matchId"]?.toUUIDOrNull() ?: throw NotFoundException("Can't join match")
        val userId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val request = call.receive<JoinMatchRequest>()
        val locale = call.retrieveLocale()
        val result = matchService.joinMatch(userId, matchId, request.team, locale)
        call.respond(result)
    }
}