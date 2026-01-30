package com.devapplab.features.user

import com.devapplab.config.getIdentifier
import com.devapplab.model.auth.ClaimType
import com.devapplab.service.UserService
import com.devapplab.utils.respond
import com.devapplab.utils.retrieveLocale
import io.ktor.server.application.*
import io.ktor.server.request.*

class UserController(private val service: UserService) {
    suspend fun getUserById(call: ApplicationCall) {
        val locale = call.retrieveLocale()
        val userId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val result = service.getUserById(userId, locale)
        call.respond(result)
    }

    suspend fun uploadProfilePic(call: ApplicationCall) {
        val locale = call.retrieveLocale()
        val userId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val multipart = call.receiveMultipart()
        val result = service.uploadProfilePic(userId, multipart, locale)
        call.respond(result)
    }
}
