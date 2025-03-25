package com.devapplab.features.auth

import com.devapplab.model.auth.JWTConfig
import com.devapplab.model.auth.request.RegisterUserRequest
import com.devapplab.service.UserService
import com.devapplab.utils.respond
import com.devapplab.utils.retrieveLocale
import io.ktor.server.application.*
import io.ktor.server.request.*
import model.user.toUser
import java.util.Locale

class AuthController(private val userService: UserService) {
    suspend fun signUp(call: ApplicationCall, jwtConfig: JWTConfig) {
        val locale: Locale = call.retrieveLocale()
        val registerUserRequest = call.receive<RegisterUserRequest>()
        val result = userService.addUser(registerUserRequest.toUser(), locale, jwtConfig)
        call.respond(result)
    }
}