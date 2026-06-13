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
import com.devapplab.observability.AuthRequestContext
import com.devapplab.observability.authFailure
import com.devapplab.observability.authRejected
import com.devapplab.observability.authRotated
import com.devapplab.observability.authSuccess
import com.devapplab.service.auth.auth_token.AuthTokenService
import com.devapplab.service.auth.refresh_token.RefreshTokenService
import com.devapplab.service.hashing.HashingService
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
    private val refreshTokenRepository: RefreshTokenRepository,
    private val hashingService: HashingService
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun refreshJwtToken(
        locale: Locale,
        currentRefreshToken: String?,
        refreshJWTRequest: RefreshJWTRequest,
        jwtConfig: JWTConfig,
        context: AuthRequestContext
    ): AppResult<AuthResponse> {
        @Suppress("UNUSED_PARAMETER")
        val ignoredRequest = refreshJWTRequest
        val plainRefresh = currentRefreshToken ?: return locale.respondInvalidRefreshTokenError()
        val hashedRefresh = hashingService.hashOpaqueToken(plainRefresh)

        val tokenRecord = runCatching {
            dbExecutor.tx {
                refreshTokenRepository.findByTokenHash(hashedRefresh)
            }
        }.getOrElse { error ->
            logger.authFailure("auth.refresh.failed", context, "db_error", throwable = error)
            return locale.createError(
                StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY
            )
        }

        if (tokenRecord == null) {
            logger.authRejected("auth.refresh.failed", context, "unknown_token")
            return locale.respondInvalidRefreshTokenError()
        }

        if (tokenRecord.revoked) {
            runCatching {
                dbExecutor.tx { refreshTokenRepository.revokeCurrentToken(tokenRecord.deviceId) }
            }.onFailure { error ->
                logger.authFailure("auth.refresh.failed", context, "db_error", tokenRecord.userId, tokenRecord.deviceId, throwable = error)
            }

            logger.authRejected("auth.refresh.reuse_detected", context, "reuse_detected", tokenRecord.userId, tokenRecord.deviceId, extra = mapOf("tokenCreatedAt" to tokenRecord.createdAt))
            return locale.respondInvalidRefreshTokenError()
        }

        val now = System.currentTimeMillis()
        if (tokenRecord.expiresAt <= now) {
            logger.authRejected("auth.refresh.failed", context, "expired_token", tokenRecord.userId, tokenRecord.deviceId, extra = mapOf("tokenCreatedAt" to tokenRecord.createdAt))
            return locale.respondInvalidRefreshTokenError()
        }

        val activeRecord = runCatching {
            dbExecutor.tx {
                refreshTokenRepository.findActiveByDeviceId(tokenRecord.deviceId)
            }
        }.getOrElse { error ->
            logger.authFailure("auth.refresh.failed", context, "db_error", tokenRecord.userId, tokenRecord.deviceId, throwable = error)
            return locale.createError(
                StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY
            )
        }

        if (activeRecord == null || activeRecord.token != tokenRecord.token) {
            runCatching {
                dbExecutor.tx { refreshTokenRepository.revokeCurrentToken(tokenRecord.deviceId) }
            }.onFailure { error ->
                logger.authFailure("auth.refresh.failed", context, "db_error", tokenRecord.userId, tokenRecord.deviceId, throwable = error)
            }

            logger.authRejected("auth.refresh.reuse_detected", context, "stale_or_non_current_token", tokenRecord.userId, tokenRecord.deviceId, extra = mapOf("tokenCreatedAt" to tokenRecord.createdAt))
            return locale.respondInvalidRefreshTokenError()
        }

        val userRole = runCatching {
            dbExecutor.tx {
                userRepository.getUserById(tokenRecord.userId)?.userRole ?: UserRole.PLAYER
            }
        }.getOrElse { error ->
            logger.authFailure("auth.refresh.failed", context, "db_error", tokenRecord.userId, tokenRecord.deviceId, throwable = error)
            return locale.createError(
                StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY
            )
        }

        val payload = RefreshTokenPayload(
            plainToken = plainRefresh,
            hashedToken = tokenRecord.token,
            expiresAt = tokenRecord.expiresAt
        )
        if (!refreshTokenService.isValidRefreshToken(payload)) {
            logger.authRejected("auth.refresh.failed", context, "hash_or_expiration_validation_failed", tokenRecord.userId, tokenRecord.deviceId)
            return locale.respondInvalidRefreshTokenError()
        }

        val claimConfig = ClaimConfig(tokenRecord.userId, userRole, tokenRecord.deviceId)
        val accessToken = authTokenService.createAuthToken(claimConfig, jwtConfig)

        val expiresSoon =
            tokenRecord.expiresAt - now < jwtConfig.refreshTokenRotationThreshold.days.inWholeMilliseconds

        var newRefreshToken: String? = null
        val authCode: AuthCode

        if (expiresSoon) {
            val newPayload = refreshTokenService.generateRefreshToken(jwtConfig.refreshTokenLifetime)

            val rotated = runCatching {
                dbExecutor.tx { authRepository.rotateRefreshToken(tokenRecord.userId, tokenRecord.deviceId, newPayload) }
                true
            }.getOrElse { error ->
                logger.authFailure("auth.refresh.failed", context, "db_error", tokenRecord.userId, tokenRecord.deviceId, throwable = error)
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
            logger.authRotated("auth.refresh.rotated", context, tokenRecord.userId, tokenRecord.deviceId)
        } else {
            authCode = AuthCode.REFRESHED_JWT
            logger.authSuccess("auth.refresh.success", context, tokenRecord.userId, tokenRecord.deviceId, extra = mapOf("rotated" to false))
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
