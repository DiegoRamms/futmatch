package com.devapplab.features.auth

import com.devapplab.config.getIdentifier
import com.devapplab.config.getOptionalIdentifier
import com.devapplab.model.auth.ClaimType
import com.devapplab.model.auth.JWTConfig
import com.devapplab.model.auth.request.*
import com.devapplab.model.auth.response.RefreshJWTRequest
import com.devapplab.model.mfa.MfaCodeRequest
import com.devapplab.model.mfa.MfaCodeVerificationRequest
import com.devapplab.model.mfa.VerifyResetMfaRequest
import com.devapplab.model.user.request.UpdatePasswordRequest
import com.devapplab.observability.AuthLogSeverity
import com.devapplab.observability.authEvent
import com.devapplab.observability.toAuthRequestContext
import com.devapplab.service.auth.AuthTokenManagementService
import com.devapplab.service.auth.PasswordResetService
import com.devapplab.service.auth.RegistrationService
import com.devapplab.service.auth.SignInService
import com.devapplab.utils.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import org.slf4j.LoggerFactory
import java.util.*

class AuthController(
    private val registrationService: RegistrationService,
    private val signInService: SignInService,
    private val passwordResetService: PasswordResetService,
    private val authTokenManagementService: AuthTokenManagementService
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun startRegistration(call: ApplicationCall) {
        val locale: Locale = call.retrieveLocale()
        val context = call.toAuthRequestContext()
        val registerUserRequest = call.receive<RegisterUserRequest>()
        val result = registrationService.startRegistration(registerUserRequest, locale, context)
        call.respond(result)
    }

    suspend fun completeRegistration(call: ApplicationCall, jwtConfig: JWTConfig) {
        val locale: Locale = call.retrieveLocale()
        val context = call.toAuthRequestContext()
        val deviceInfo = call.getUserAgentHeader()
        val request = call.receive<CompleteRegistrationRequest>()
        val result = registrationService.completeRegistration(request, jwtConfig, locale, deviceInfo, context)
        call.respond(result)
    }

    suspend fun resendRegistrationCode(call: ApplicationCall) {
        val locale: Locale = call.retrieveLocale()
        val context = call.toAuthRequestContext()
        val request = call.receive<ResendRegistrationCodeRequest>()
        val result = registrationService.resendRegistrationCode(request, locale, context)
        call.respond(result)
    }

    suspend fun signIn(call: ApplicationCall, jwtConfig: JWTConfig) {
        val locale: Locale = call.retrieveLocale()
        val context = call.toAuthRequestContext()
        val deviceInfo = call.getUserAgentHeader()
        val request = call.receive<SignInRequest>()
        val result = signInService.signIn(locale, request, jwtConfig, deviceInfo, context)
        call.respond(result)
    }

    suspend fun refreshJWT(call: ApplicationCall, jwtConfig: JWTConfig) {
        val locale: Locale = call.retrieveLocale()
        val context = call.toAuthRequestContext()
        val refreshToken = call.getRefreshToken()
        val refreshJWTRequest = call.receive<RefreshJWTRequest>()
        val result = authTokenManagementService.refreshJwtToken(locale, refreshToken, refreshJWTRequest, jwtConfig, context)
        call.respond(result)
    }

    suspend fun sendMfaCode(call: ApplicationCall) {
        val locale: Locale = call.retrieveLocale()
        val context = call.toAuthRequestContext()
        val mfaCodeRequest = call.receive<MfaCodeRequest>()
        val result = signInService.sendMFACode(locale, mfaCodeRequest, context)
        call.respond(result)
    }

    suspend fun verifyMfaCode(call: ApplicationCall, jwtConfig: JWTConfig) {
        val locale: Locale = call.retrieveLocale()
        val context = call.toAuthRequestContext()
        val mfaCodeVerificationRequest = call.receive<MfaCodeVerificationRequest>()
        val result = signInService.verifyMfaCode(locale, mfaCodeVerificationRequest, jwtConfig, context)
        call.respond(result)
    }

    suspend fun signOut(call: ApplicationCall){
        val locale: Locale = call.retrieveLocale()
        val context = call.toAuthRequestContext()
        val userId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val request = call.receive<SignOutRequest>()
        val deviceIdFromJwt = call.getOptionalIdentifier(ClaimType.DEVICE_IDENTIFIER)
        val resolvedDeviceId = if (deviceIdFromJwt != null) {
            deviceIdFromJwt
        } else {
            // TODO: Remove legacy signOut deviceId fallback once old access JWTs have expired from all clients.
            val legacyDeviceId = request.deviceId
                ?: throw InvalidTokenException("Missing device identifier for sign out")
            logger.authEvent(
                severity = AuthLogSeverity.WARN,
                event = "auth.sign_out.legacy_fallback",
                context = context,
                outcome = "rejected",
                reason = "missing_device_identifier_claim",
                userId = userId,
                deviceId = legacyDeviceId
            )
            legacyDeviceId
        }
        val result = signInService.signOut(locale, userId, resolvedDeviceId, context)
        call.respond(result)
    }

    suspend fun forgotPassword(call: ApplicationCall) {
        val locale: Locale = call.retrieveLocale()
        val context = call.toAuthRequestContext()
        val request = call.receive<ForgotPasswordRequest>()
        val result = passwordResetService.forgotPassword(locale, request, context)
        call.respond(result)
    }

    suspend fun verifyResetMfa(call: ApplicationCall) {
        val locale: Locale = call.retrieveLocale()
        val context = call.toAuthRequestContext()
        val request = call.receive<VerifyResetMfaRequest>()
        val result = passwordResetService.verifyResetMfa(locale, request, context)
        call.respond(result)
    }

    suspend fun updatePassword(call: ApplicationCall) {
        val locale = call.retrieveLocale()
        val context = call.toAuthRequestContext()
        val resetToken = call.getResetToken()
        val request = call.receive<UpdatePasswordRequest>()
        val result = passwordResetService.updatePassword(resetToken, request, locale, context)
        call.respond(result)
    }
}
