package com.devapplab.features.auth

import com.devapplab.model.auth.JWTConfig
import com.devapplab.model.auth.request.ForgotPasswordRequest
import com.devapplab.model.auth.request.RegisterUserRequest
import com.devapplab.model.auth.request.SignInRequest
import com.devapplab.model.auth.request.SignOutRequest
import com.devapplab.model.auth.response.RefreshJWTRequest
import com.devapplab.service.auth.AuthService
import com.devapplab.utils.getRefreshToken
import com.devapplab.utils.getUserAgentHeader
import com.devapplab.utils.respond
import com.devapplab.utils.retrieveLocale
import io.ktor.server.application.*
import io.ktor.server.request.*
import model.mfa.MfaCodeRequest
import model.mfa.MfaCodeVerificationRequest
import model.user.toUser
import java.util.*

class AuthController(private val authService: AuthService) {
    suspend fun signUp(call: ApplicationCall) {
        val locale: Locale = call.retrieveLocale()
        val deviceInfo = call.getUserAgentHeader()
        val registerUserRequest = call.receive<RegisterUserRequest>()
        val result = authService.addUser(registerUserRequest.toUser(), locale, deviceInfo)
        call.respond(result)
    }

    suspend fun signIn(call: ApplicationCall, jwtConfig: JWTConfig) {
        val locale: Locale = call.retrieveLocale()
        val deviceInfo = call.getUserAgentHeader()
        val request = call.receive<SignInRequest>()
        val result = authService.signIn(locale, request, jwtConfig, deviceInfo)
        call.respond(result)
    }

    suspend fun refreshJWT(call: ApplicationCall, jwtConfig: JWTConfig) {
        val locale: Locale = call.retrieveLocale()
        val refreshToken = call.getRefreshToken()
        val refreshJWTRequest = call.receive<RefreshJWTRequest>()
        val result = authService.refreshJwtToken(locale, refreshToken, refreshJWTRequest, jwtConfig)
        call.respond(result)
    }

    suspend fun sendMfaCode(call: ApplicationCall) {
        val locale: Locale = call.retrieveLocale()
        val mfaCodeRequest = call.receive<MfaCodeRequest>()
        val result = authService.sendMFACode(locale, mfaCodeRequest)
        call.respond(result)
    }

    suspend fun verifyMfaCode(call: ApplicationCall, jwtConfig: JWTConfig) {
        val locale: Locale = call.retrieveLocale()
        val mfaCodeVerificationRequest = call.receive<MfaCodeVerificationRequest>()
        val result = authService.verifyMfaCode(locale, mfaCodeVerificationRequest, jwtConfig)
        call.respond(result)
    }

    suspend fun signOut(call: ApplicationCall){
        val locale: Locale = call.retrieveLocale()
        val request = call.receive<SignOutRequest>()
        val result = authService.signOut(locale, request.deviceId)
        call.respond(result)
    }

    suspend fun forgotPassword(call: ApplicationCall) {
        val locale: Locale = call.retrieveLocale()
        val request = call.receive<ForgotPasswordRequest>()
        val result = authService.forgotPassword(locale, request)
        call.respond(result)
    }
}
