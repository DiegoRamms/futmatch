package com.devapplab.service.auth

import com.devapplab.data.database.executor.DbExecutor
import com.devapplab.data.repository.auth.AuthRepository
import com.devapplab.model.AppResult
import com.devapplab.model.ErrorCode
import com.devapplab.model.auth.ClaimConfig
import com.devapplab.model.auth.JWTConfig
import com.devapplab.model.auth.response.AuthCode
import com.devapplab.model.auth.response.AuthResponse
import com.devapplab.model.auth.response.AuthTokenResponse
import com.devapplab.model.user.UserRole
import com.devapplab.service.auth.auth_token.AuthTokenService
import com.devapplab.service.auth.refresh_token.RefreshTokenService
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.createError
import io.ktor.http.*
import org.slf4j.LoggerFactory
import java.util.*

class AuthenticatedResponseGenerator(
    private val dbExecutor: DbExecutor,
    private val authTokenService: AuthTokenService,
    private val refreshTokenService: RefreshTokenService,
    private val authRepository: AuthRepository
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun generate(
        locale: Locale,
        userId: UUID,
        deviceId: UUID,
        userRole: UserRole,
        jwtConfig: JWTConfig
    ): AppResult<AuthResponse> {
        val start = System.currentTimeMillis()

        val claimConfig = ClaimConfig(userId, userRole)
        val accessToken = authTokenService.createAuthToken(claimConfig, jwtConfig)
        val refreshTokenPayload = refreshTokenService.generateRefreshToken(jwtConfig.refreshTokenLifetime)

        val rotated = runCatching {
            dbExecutor.tx {
                authRepository.rotateRefreshToken(userId, deviceId, refreshTokenPayload)
            }
            true
        }.getOrElse { error ->
            logger.error("🔥 rotateRefreshToken failed for userId=$userId deviceId=$deviceId", error)
            false
        }

        if (!rotated) {
            return locale.createError(
                StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY,
                status = HttpStatusCode.InternalServerError,
                errorCode = ErrorCode.GENERAL_ERROR
            )
        }

        val duration = System.currentTimeMillis() - start
        logger.info("✅ JWT + RefreshToken generated in $duration ms")

        return AppResult.Success(
            AuthResponse(
                authTokenResponse = AuthTokenResponse(
                    accessToken = accessToken,
                    refreshToken = refreshTokenPayload.plainToken
                ),
                userId = userId,
                deviceId = deviceId,
                authCode = AuthCode.SUCCESS
            )
        )
    }
}
