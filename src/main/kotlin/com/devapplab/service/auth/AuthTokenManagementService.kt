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
import com.devapplab.model.auth.RefreshTokenRecord
import com.devapplab.model.auth.response.AuthCode
import com.devapplab.model.auth.response.AuthResponse
import com.devapplab.model.auth.response.AuthTokenResponse
import com.devapplab.model.auth.response.RefreshJWTRequest
import com.devapplab.model.user.UserRole
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
        jwtConfig: JWTConfig
    ): AppResult<AuthResponse> {

        val plainRefresh = currentRefreshToken ?: return locale.respondInvalidRefreshTokenError()
        val hashedRefresh = hashingService.hashOpaqueToken(plainRefresh)
        val requestedUserId = refreshJWTRequest.userId
        val requestedDeviceId = refreshJWTRequest.deviceId

        val tokenRecord = runCatching {
            dbExecutor.tx {
                refreshTokenRepository.findByTokenHash(hashedRefresh)
            }
        }.getOrElse { error ->
            logger.error("🔥 auth.refresh.lookup_failed requestedUserId=$requestedUserId requestedDeviceId=$requestedDeviceId", error)
            return locale.createError(
                StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY
            )
        }

        if (tokenRecord == null) {
            logger.warn("⚠️ auth.refresh.invalid unknown_token requestedUserId=$requestedUserId requestedDeviceId=$requestedDeviceId")
            return locale.respondInvalidRefreshTokenError()
        }

        if (requestedUserId != null || requestedDeviceId != null) {
            // TODO: Remove legacy userId/deviceId refresh payload support once all clients use refresh-token-only requests.
            logger.warn(
                "Deprecated legacy refresh payload used. Prefer refresh-token-only flow. requestedUserId={} requestedDeviceId={} tokenUserId={} tokenDeviceId={}",
                requestedUserId,
                requestedDeviceId,
                tokenRecord.userId,
                tokenRecord.deviceId
            )
            if (requestedUserId != tokenRecord.userId || requestedDeviceId != tokenRecord.deviceId) {
                logger.warn(
                    "⚠️ auth.refresh.legacy_mismatch requestedUserId={} requestedDeviceId={} tokenUserId={} tokenDeviceId={}",
                    requestedUserId,
                    requestedDeviceId,
                    tokenRecord.userId,
                    tokenRecord.deviceId
                )
                return locale.respondInvalidRefreshTokenError()
            }
        }

        if (tokenRecord.revoked) {
            runCatching {
                dbExecutor.tx { refreshTokenRepository.revokeCurrentToken(tokenRecord.deviceId) }
            }.onFailure { error ->
                logger.error(
                    "🔥 auth.refresh.reuse_detected revoke_current_failed userId={} deviceId={}",
                    tokenRecord.userId,
                    tokenRecord.deviceId,
                    error
                )
            }

            logger.warn(
                "⚠️ auth.refresh.reuse_detected userId={} deviceId={} tokenCreatedAt={}",
                tokenRecord.userId,
                tokenRecord.deviceId,
                tokenRecord.createdAt
            )
            return locale.respondInvalidRefreshTokenError()
        }

        val now = System.currentTimeMillis()
        if (tokenRecord.expiresAt <= now) {
            logger.warn(
                "⚠️ auth.refresh.invalid expired_token userId={} deviceId={} tokenCreatedAt={}",
                tokenRecord.userId,
                tokenRecord.deviceId,
                tokenRecord.createdAt
            )
            return locale.respondInvalidRefreshTokenError()
        }

        val activeRecord = runCatching {
            dbExecutor.tx {
                refreshTokenRepository.findActiveByDeviceId(tokenRecord.deviceId)
            }
        }.getOrElse { error ->
            logger.error("🔥 auth.refresh.active_lookup_failed userId={} deviceId={}", tokenRecord.userId, tokenRecord.deviceId, error)
            return locale.createError(
                StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY
            )
        }

        if (activeRecord == null || activeRecord.token != tokenRecord.token) {
            runCatching {
                dbExecutor.tx { refreshTokenRepository.revokeCurrentToken(tokenRecord.deviceId) }
            }.onFailure { error ->
                logger.error(
                    "🔥 auth.refresh.reuse_detected revoke_current_failed userId={} deviceId={}",
                    tokenRecord.userId,
                    tokenRecord.deviceId,
                    error
                )
            }

            logger.warn(
                "⚠️ auth.refresh.reuse_detected stale_or_non_current_token userId={} deviceId={} tokenCreatedAt={}",
                tokenRecord.userId,
                tokenRecord.deviceId,
                tokenRecord.createdAt
            )
            return locale.respondInvalidRefreshTokenError()
        }

        val userRole = runCatching {
            dbExecutor.tx {
                userRepository.getUserById(tokenRecord.userId)?.userRole ?: UserRole.PLAYER
            }
        }.getOrElse { error ->
            logger.error("🔥 auth.refresh.user_lookup_failed userId={} deviceId={}", tokenRecord.userId, tokenRecord.deviceId, error)
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
            logger.warn(
                "⚠️ auth.refresh.invalid hash_or_expiration_validation_failed userId={} deviceId={}",
                tokenRecord.userId,
                tokenRecord.deviceId
            )
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
                logger.error("🔥 auth.refresh.rotate_failed userId={} deviceId={}", tokenRecord.userId, tokenRecord.deviceId, error)
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
            logger.info("✅ auth.refresh.rotated userId={} deviceId={}", tokenRecord.userId, tokenRecord.deviceId)
        } else {
            authCode = AuthCode.REFRESHED_JWT
            logger.info("✅ auth.refresh.success userId={} deviceId={} rotated=false", tokenRecord.userId, tokenRecord.deviceId)
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
