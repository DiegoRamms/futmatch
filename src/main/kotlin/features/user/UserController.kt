package com.devapplab.features.user

import com.devapplab.service.UserService
import com.devapplab.utils.respond
import com.devapplab.utils.retrieveLocale
import com.devapplab.utils.toUUIDOrNull
import io.ktor.server.application.*

class UserController(private val service: UserService) {
    suspend fun getUserById(call: ApplicationCall) {
        val id = call.parameters["id"]?.toUUIDOrNull()
        val locale = call.retrieveLocale()
        val result = service.getUserById(id, locale)
        println(result)
        call.respond(result)
    }
}