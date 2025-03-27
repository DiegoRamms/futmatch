package com.devapplab.service.auth

import com.devapplab.data.database.refresh_token.RefreshTokenDao
import com.devapplab.data.repository.UserRepository
import com.devapplab.model.AppResult
import com.devapplab.model.ErrorCode
import com.devapplab.model.auth.ClaimConfig
import com.devapplab.model.auth.JWTConfig
import com.devapplab.model.auth.RefreshTokenPayload
import com.devapplab.model.auth.response.AuthCode
import com.devapplab.model.auth.response.AuthResponse
import com.devapplab.model.auth.response.AuthTokenResponse
import com.devapplab.model.auth.response.RefreshJWTRequest
import com.devapplab.service.auth.auth_token.AuthTokenService
import com.devapplab.service.auth.refresh_token.RefreshTokenService
import com.devapplab.service.hashing.HashingService
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.TWO_DAYS_IN_MS
import com.devapplab.utils.createError
import io.ktor.http.*
import model.user.User
import java.util.*

class AuthService(
    private val userRepository: UserRepository,
    private val hashingService: HashingService,
    private val authTokenService: AuthTokenService,
    private val refreshTokenService: RefreshTokenService,
    private val refreshTokenDao: RefreshTokenDao,
    private val deviceService: DeviceService
) {
    suspend fun addUser(user: User, locale: Locale, jwtConfig: JWTConfig): AppResult<AuthResponse> {
        val isEmailAlreadyRegistered = userRepository.isEmailAlreadyRegistered(user.email)
        val isPhoneNumberAlreadyRegistered = userRepository.isPhoneNumberAlreadyRegistered(user.phone)

        if (isEmailAlreadyRegistered) return locale.respondIsEmailAlreadyRegisteredError()
        if (isPhoneNumberAlreadyRegistered) return locale.respondIsPhoneAlreadyRegisteredError()

        val userWithPasswordHashed = user.copy(password = hashingService.hash(user.password))
        val userId = userRepository.addUser(userWithPasswordHashed)
        val claimConfig = ClaimConfig(userId, false)
        val deviceId = deviceService.generateDeviceId()
        val token = authTokenService.createAuthToken(claimConfig, jwtConfig)
        val refreshTokenPayload = refreshTokenService.generateRefreshToken()

        refreshTokenService.saveRefreshToken(userId, deviceId, refreshTokenPayload)

        val authTokenResponse = AuthTokenResponse(token, refreshTokenPayload.plainToken, deviceId)
        val authResponse = AuthResponse(authTokenResponse, authCode = AuthCode.USER_CREATED)

        return AppResult.Success(authResponse)
    }

    suspend fun refreshJwtToken(
        locale: Locale,
        currentRefreshToken: String?,
        refreshJWTRequest: RefreshJWTRequest,
        jwtConfig: JWTConfig
    ): AppResult<AuthResponse> {
        val (userId, deviceId) = refreshJWTRequest
        val refreshTokenValidationInfo = refreshTokenDao.getRefreshTokenValidationInfo(deviceId)
            ?: return locale.respondInvalidRefreshTokenError()

        val refreshTokenPayload = RefreshTokenPayload(
            plainToken = currentRefreshToken ?: return locale.respondInvalidRefreshTokenError(),
            hashedToken = refreshTokenValidationInfo.token,
            expiresAt = refreshTokenValidationInfo.expiresAt
        )

        val isRefreshTokenValid = refreshTokenService.isValidRefreshToken(refreshTokenPayload)
        if (!isRefreshTokenValid) return locale.respondInvalidRefreshTokenError()

        val isEmailVerified = userRepository.isEmailVerified(userId)
        val claimConfig = ClaimConfig(userId, isEmailVerified)
        val accessToken = authTokenService.createAuthToken(claimConfig, jwtConfig)

        val expiresSoon = refreshTokenValidationInfo.expiresAt - System.currentTimeMillis() < TWO_DAYS_IN_MS

        val (refreshToken, authCode) = if (expiresSoon) {
            val newPayload = refreshTokenService.generateRefreshToken()
            refreshTokenService.saveRefreshToken(userId, deviceId, newPayload)
            refreshTokenService.revokeRefreshToken(deviceId)
            newPayload.plainToken to AuthCode.REFRESHED_BOTH_TOKENS
        } else {
            null to AuthCode.REFRESHED_JWT
        }

        val authResponse = AuthResponse(
            authTokenResponse = AuthTokenResponse(accessToken = accessToken, refreshToken = refreshToken),
            authCode = authCode
        )

        return AppResult.Success(authResponse)
    }

    private fun Locale.respondIsEmailAlreadyRegisteredError(): AppResult.Failure =
        createError(
            StringResourcesKey.REGISTER_EMAIL_ALREADY_EXISTS_TITLE,
            StringResourcesKey.REGISTER_EMAIL_ALREADY_EXISTS_DESCRIPTION,
            status = HttpStatusCode.Conflict
        )

    private fun Locale.respondIsPhoneAlreadyRegisteredError(): AppResult.Failure =
        createError(
            StringResourcesKey.REGISTER_PHONE_ALREADY_EXISTS_TITLE,
            StringResourcesKey.REGISTER_PHONE_ALREADY_EXISTS_DESCRIPTION,
            status = HttpStatusCode.Conflict,
        )

    private fun Locale.respondInvalidRefreshTokenError(): AppResult.Failure =
        createError(
            StringResourcesKey.AUTH_REFRESH_TOKEN_INVALID_TITLE,
            StringResourcesKey.AUTH_REFRESH_TOKEN_INVALID_DESCRIPTION,
            status = HttpStatusCode.Unauthorized,
            errorCode = ErrorCode.AUTH_NEED_LOGIN
        )
}