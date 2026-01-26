package com.devapplab.service.auth

import com.devapplab.data.database.executor.DbExecutor
import com.devapplab.data.repository.RefreshTokenRepository
import com.devapplab.data.repository.auth.AuthRepository
import com.devapplab.data.repository.user.UserRepository
import com.devapplab.model.AppResult
import com.devapplab.model.ErrorCode
import com.devapplab.model.auth.ClaimConfig
import com.devapplab.model.auth.JWTConfig
import com.devapplab.model.auth.RefreshTokenPayload
import com.devapplab.model.auth.response.AuthCode
import com.devapplab.model.auth.response.AuthResponse
import com.devapplab.model.auth.response.AuthTokenResponse
import com.devapplab.model.auth.response.RefreshJWTRequest
import com.devapplab.model.user.UserRole
import com.devapplab.service.auth.auth_token.AuthTokenService
import com.devapplab.service.auth.refresh_token.RefreshTokenService
import com.devapplab.service.auth.state.RefreshDbData
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.createError
import io.ktor.http.*
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.time.Duration.Companion.days


class AuthTokenManagementService(
    private val dbExecutor: DbExecutor,
    private val userRepository: UserRepository,
    private val authTokenService: AuthTokenService,
    private val refreshTokenService: RefreshTokenService,
    private val authRepository: AuthRepository,
    private val refreshTokenRepository: RefreshTokenRepository
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun refreshJwtToken(
        locale: Locale,
        currentRefreshToken: String?,
        refreshJWTRequest: RefreshJWTRequest,
        jwtConfig: JWTConfig
    ): AppResult<AuthResponse> {

        val (userId, deviceId) = refreshJWTRequest
        val plainRefresh = currentRefreshToken ?: return locale.respondInvalidRefreshTokenError()

        val dbData = runCatching {
            dbExecutor.tx {
                val validationInfo = refreshTokenRepository.getValidationInfo(deviceId) ?: return@tx null
                val userRole = userRepository.getUserById(userId)?.userRole ?: UserRole.PLAYER
                RefreshDbData(validationInfo, userRole)
            }
        }.getOrElse { error ->
            logger.error("🔥 refreshJwtToken DB read error userId=$userId deviceId=$deviceId", error)
            return locale.createError(
                StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY
            )
        } ?: return locale.respondInvalidRefreshTokenError()

        val validationInfo = dbData.validationInfo
        val userRole = dbData.userRole

        val payload = RefreshTokenPayload(
            plainToken = plainRefresh,
            hashedToken = validationInfo.token,
            expiresAt = validationInfo.expiresAt
        )

        if (!refreshTokenService.isValidRefreshToken(payload)) {
            return locale.respondInvalidRefreshTokenError()
        }

        val claimConfig = ClaimConfig(userId, userRole)
        val accessToken = authTokenService.createAuthToken(claimConfig, jwtConfig)

        val now = System.currentTimeMillis()
        val expiresSoon =
            validationInfo.expiresAt - now < jwtConfig.refreshTokenRotationThreshold.days.inWholeMilliseconds

        var newRefreshToken: String? = null
        val authCode: AuthCode

        if (expiresSoon) {
            val newPayload = refreshTokenService.generateRefreshToken(jwtConfig.refreshTokenLifetime)

            val rotated = runCatching {
                dbExecutor.tx { authRepository.rotateRefreshToken(userId, deviceId, newPayload) }
                true
            }.getOrElse { error ->
                logger.error("🔥 refreshJwtToken rotateRefreshToken failed userId=$userId deviceId=$deviceId", error)
                false
            }

            if (!rotated) {
                return locale.createError(
                    StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                    StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY
                )
            }

            newRefreshToken = newPayload.plainToken
            authCode = AuthCode.REFRESHED_BOTH_TOKENS
        } else {
            authCode = AuthCode.REFRESHED_JWT
        }

        return AppResult.Success(
            AuthResponse(
                authTokenResponse = AuthTokenResponse(
                    accessToken = accessToken,
                    refreshToken = newRefreshToken
                ),
                authCode = authCode
            )
        )
    }

    private fun Locale.respondInvalidRefreshTokenError(): AppResult.Failure =
        createError(
            StringResourcesKey.AUTH_REFRESH_TOKEN_INVALID_TITLE,
            StringResourcesKey.AUTH_REFRESH_TOKEN_INVALID_DESCRIPTION,
            status = HttpStatusCode.Unauthorized,
            errorCode = ErrorCode.AUTH_NEED_LOGIN
        )
}
