package com.devapplab.features.auth

import com.devapplab.model.auth.JWTConfig
import com.devapplab.model.auth.request.*
import com.devapplab.model.auth.response.RefreshJWTRequest
import com.devapplab.model.mfa.MfaCodeRequest
import com.devapplab.model.mfa.MfaCodeVerificationRequest
import com.devapplab.model.mfa.VerifyResetMfaRequest
import com.devapplab.model.user.request.UpdatePasswordRequest
import com.devapplab.service.auth.AuthTokenManagementService
import com.devapplab.service.auth.PasswordResetService
import com.devapplab.service.auth.RegistrationService
import com.devapplab.service.auth.SignInService
import com.devapplab.utils.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import java.util.*

class AuthController(
    private val registrationService: RegistrationService,
    private val signInService: SignInService,
    private val passwordResetService: PasswordResetService,
    private val authTokenManagementService: AuthTokenManagementService
) {

    suspend fun startRegistration(call: ApplicationCall) {
        val locale: Locale = call.retrieveLocale()
        val registerUserRequest = call.receive<RegisterUserRequest>()
        val result = registrationService.startRegistration(registerUserRequest, locale)
        call.respond(result)
    }

    suspend fun completeRegistration(call: ApplicationCall, jwtConfig: JWTConfig) {
        val locale: Locale = call.retrieveLocale()
        val deviceInfo = call.getUserAgentHeader()
        val request = call.receive<CompleteRegistrationRequest>()
        val result = registrationService.completeRegistration(request, jwtConfig, locale, deviceInfo)
        call.respond(result)
    }

    suspend fun resendRegistrationCode(call: ApplicationCall) {
        val locale: Locale = call.retrieveLocale()
        val request = call.receive<ResendRegistrationCodeRequest>()
        val result = registrationService.resendRegistrationCode(request, locale)
        call.respond(result)
    }

    suspend fun signIn(call: ApplicationCall, jwtConfig: JWTConfig) {
        val locale: Locale = call.retrieveLocale()
        val deviceInfo = call.getUserAgentHeader()
        val request = call.receive<SignInRequest>()
        val result = signInService.signIn(locale, request, jwtConfig, deviceInfo)
        call.respond(result)
    }

    suspend fun refreshJWT(call: ApplicationCall, jwtConfig: JWTConfig) {
        val locale: Locale = call.retrieveLocale()
        val refreshToken = call.getRefreshToken()
        val refreshJWTRequest = call.receive<RefreshJWTRequest>()
        val result = authTokenManagementService.refreshJwtToken(locale, refreshToken, refreshJWTRequest, jwtConfig)
        call.respond(result)
    }

    suspend fun sendMfaCode(call: ApplicationCall) {
        val locale: Locale = call.retrieveLocale()
        val mfaCodeRequest = call.receive<MfaCodeRequest>()
        val result = signInService.sendMFACode(locale, mfaCodeRequest)
        call.respond(result)
    }

    suspend fun verifyMfaCode(call: ApplicationCall, jwtConfig: JWTConfig) {
        val locale: Locale = call.retrieveLocale()
        val mfaCodeVerificationRequest = call.receive<MfaCodeVerificationRequest>()
        val result = signInService.verifyMfaCode(locale, mfaCodeVerificationRequest, jwtConfig)
        call.respond(result)
    }

    suspend fun signOut(call: ApplicationCall){
        val locale: Locale = call.retrieveLocale()
        val request = call.receive<SignOutRequest>()
        val result = signInService.signOut(locale, request.deviceId)
        call.respond(result)
    }

    suspend fun forgotPassword(call: ApplicationCall) {
        val locale: Locale = call.retrieveLocale()
        val request = call.receive<ForgotPasswordRequest>()
        val result = passwordResetService.forgotPassword(locale, request)
        call.respond(result)
    }

    suspend fun verifyResetMfa(call: ApplicationCall) {
        val locale: Locale = call.retrieveLocale()
        val request = call.receive<VerifyResetMfaRequest>()
        val result = passwordResetService.verifyResetMfa(locale, request)
        call.respond(result)
    }

    suspend fun updatePassword(call: ApplicationCall) {
        val locale = call.retrieveLocale()
        val resetToken = call.getResetToken()
        val request = call.receive<UpdatePasswordRequest>()
        val result = passwordResetService.updatePassword(resetToken, request, locale)
        call.respond(result)
    }
}
