package com.devapplab.features.user

import com.devapplab.config.getIdentifier
import com.devapplab.model.auth.ClaimType
import com.devapplab.model.user.request.UpdateCountryRequest
import com.devapplab.model.user.request.UpdateGenderRequest
import com.devapplab.model.user.request.UpdateNameRequest
import com.devapplab.model.user.request.UpdatePositionRequest
import com.devapplab.model.user.request.DeleteAccountRequest
import com.devapplab.observability.requestContext
import com.devapplab.service.UserService
import com.devapplab.utils.respond
import com.devapplab.utils.retrieveLocale
import io.ktor.server.application.*
import io.ktor.server.request.*

class UserController(private val service: UserService) {
    suspend fun getHome(call: ApplicationCall) {
        val locale = call.retrieveLocale()
        val userId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val result = service.getHome(userId, locale, call.requestContext())
        call.respond(result)
    }

    suspend fun getUserById(call: ApplicationCall) {
        val locale = call.retrieveLocale()
        val userId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val result = service.getUserById(userId, locale, call.requestContext())
        call.respond(result)
    }

    suspend fun uploadProfilePic(call: ApplicationCall) {
        val locale = call.retrieveLocale()
        val userId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val multipart = call.receiveMultipart()
        val result = service.uploadProfilePic(userId, multipart, locale, call.requestContext())
        call.respond(result)
    }

    suspend fun getOrganizers(call: ApplicationCall) {
        val result = service.getOrganizers()
        call.respond(result)
    }

    suspend fun getPaymentHistory(call: ApplicationCall) {
        val userId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val result = service.getPaymentHistory(userId)
        call.respond(result)
    }

    suspend fun updateName(call: ApplicationCall) {
        val locale = call.retrieveLocale()
        val userId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val request = call.receive<UpdateNameRequest>()
        val result = service.updateName(userId, request.name, request.lastName, locale, call.requestContext())
        call.respond(result)
    }

    suspend fun updateCountry(call: ApplicationCall) {
        val locale = call.retrieveLocale()
        val userId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val request = call.receive<UpdateCountryRequest>()
        val result = service.updateCountry(userId, request.countryCode, locale, call.requestContext())
        call.respond(result)
    }

    suspend fun updateGender(call: ApplicationCall) {
        val locale = call.retrieveLocale()
        val userId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val request = call.receive<UpdateGenderRequest>()
        val result = service.updateGender(userId, request.gender, locale, call.requestContext())
        call.respond(result)
    }

    suspend fun updatePosition(call: ApplicationCall) {
        val locale = call.retrieveLocale()
        val userId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val request = call.receive<UpdatePositionRequest>()
        val result = service.updatePosition(userId, request.position, locale, call.requestContext())
        call.respond(result)
    }

    suspend fun deleteAccount(call: ApplicationCall) {
        val locale = call.retrieveLocale()
        val userId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val request = call.receive<DeleteAccountRequest>()
        val result = service.deleteAccount(userId, request.password, request.confirmation, locale, call.requestContext())
        call.respond(result)
    }
}
