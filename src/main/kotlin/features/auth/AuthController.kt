package com.devapplab.features.auth

import com.devapplab.model.auth.JWTConfig
import com.devapplab.model.auth.request.RegisterUserRequest
import com.devapplab.model.auth.response.RefreshJWTRequest
import com.devapplab.service.auth.AuthService
import com.devapplab.utils.getAuthorizationHeader
import com.devapplab.utils.respond
import com.devapplab.utils.retrieveLocale
import io.ktor.server.application.*
import io.ktor.server.request.*
import model.user.toUser
import java.util.*

class AuthController(private val authService: AuthService) {
    suspend fun signUp(call: ApplicationCall, jwtConfig: JWTConfig) {
        val locale: Locale = call.retrieveLocale()
        val registerUserRequest = call.receive<RegisterUserRequest>()
        val result = authService.addUser(registerUserRequest.toUser(), locale, jwtConfig)
        call.respond(result)
    }

    suspend fun refreshJWT(call: ApplicationCall, jwtConfig: JWTConfig) {
        val locale: Locale = call.retrieveLocale()
        val refreshToken = call.getAuthorizationHeader()
        val refreshJWTRequest = call.receive<RefreshJWTRequest>()
        val result = authService.refreshJwtToken(locale, refreshToken, refreshJWTRequest, jwtConfig)
        call.respond(result)
    }
}