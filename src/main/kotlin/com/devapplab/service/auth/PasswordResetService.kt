package com.devapplab.service.auth

import com.devapplab.data.database.executor.DbExecutor
import com.devapplab.data.repository.auth.AuthRepository
import com.devapplab.data.repository.login_attempt.LoginAttemptRepository
import com.devapplab.data.repository.password_reset.PasswordResetTokenRepository
import com.devapplab.data.repository.user.UserRepository
import com.devapplab.model.AppResult
import com.devapplab.model.ErrorCode
import com.devapplab.model.auth.request.ForgotPasswordRequest
import com.devapplab.model.auth.response.ForgotPasswordResponse
import com.devapplab.model.auth.response.VerifyResetMfaResponse
import com.devapplab.model.mfa.MfaChannel
import com.devapplab.model.mfa.MfaCreationResult
import com.devapplab.model.mfa.MfaPurpose
import com.devapplab.model.mfa.VerifyResetMfaRequest
import com.devapplab.model.user.UserStatus
import com.devapplab.model.user.request.UpdatePasswordRequest
import com.devapplab.model.user.response.UpdatePasswordResponse
import com.devapplab.service.auth.mfa.MfaCodeService
import com.devapplab.service.auth.mfa.MfaRateLimitConfig
import com.devapplab.service.auth.state.UpdatePasswordTxResult
import com.devapplab.service.email.EmailService
import com.devapplab.service.hashing.HashingService
import com.devapplab.service.password_reset.PasswordResetTokenService
import com.devapplab.utils.MfaUtils
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.createError
import com.devapplab.utils.getString
import io.ktor.http.*
import org.slf4j.LoggerFactory
import java.util.*

class PasswordResetService(
    private val dbExecutor: DbExecutor,
    private val userRepository: UserRepository,
    private val hashingService: HashingService,
    private val passwordResetTokenService: PasswordResetTokenService,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val mfaCodeService: MfaCodeService,
    private val emailService: EmailService,
    private val authRepository: AuthRepository,
    private val loginAttemptRepository: LoginAttemptRepository,
    private val mfaRateLimitConfig: MfaRateLimitConfig
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun forgotPassword(
        locale: Locale,
        forgotPasswordRequest: ForgotPasswordRequest
    ): AppResult<ForgotPasswordResponse> {

        val email = forgotPasswordRequest.email

        // 1) DB read (tx corta)
        val user = runCatching {
            dbExecutor.tx { userRepository.findByEmail(email) }
        }.getOrElse { error ->
            logger.error("🔥 forgotPassword getUser DB error email=$email", error)
            return locale.createError(
                StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY
            )
        } ?: return locale.respondUserNotFoundError()

        if (user.status == UserStatus.BLOCKED) {
            return locale.respondSignInBlockedUserError()
        }
        if (!user.isEmailVerified) {
            return locale.respondUserNotVerifiedError()
        }

        // 2) CPU fuera de tx
        val code = MfaUtils.generateCode()
        val expiresAt = MfaUtils.calculateExpiration(300)
        val hashedMfaCode = hashingService.hashOpaqueToken(code)

        // 3) DB: rate-limit + deactivate + insert (tx corta)
        val creationResult = runCatching {
            dbExecutor.tx {
                mfaCodeService.createMfaCode(
                    userId = user.id,
                    deviceId = null,
                    hashedCode = hashedMfaCode,
                    channel = MfaChannel.EMAIL,
                    purpose = MfaPurpose.PASSWORD_RESET,
                    expiresAt = expiresAt,
                    config = mfaRateLimitConfig
                )
            }
        }.getOrElse { error ->
            logger.error("🔥 forgotPassword createMfaCode DB error userId=${user.id}", error)
            return locale.createError(
                StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY
            )
        }

        return when (creationResult) {
            is MfaCreationResult.Created -> {
                runCatching {
                    emailService.sendMfaPasswordResetEmail(user.email, code, locale)
                    logger.info("✅ Sent password reset code to user ${user.id}")
                }.getOrElse { error ->
                    logger.error("📧 Failed to send password reset email to userId=${user.id}", error)
                    return locale.createError(
                        StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                        StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY
                    )
                }

                AppResult.Success(
                    ForgotPasswordResponse(
                        userId = user.id,
                        newCodeSent = true,
                        expiresInSeconds = creationResult.expiresInSeconds,
                        resendCodeTimeInSeconds = mfaRateLimitConfig.minWaitSeconds
                    )
                )
            }

            is MfaCreationResult.Cooldown -> {
                locale.createError(
                    titleKey = StringResourcesKey.MFA_COOLDOWN_TITLE,
                    descriptionKey = StringResourcesKey.MFA_COOLDOWN_DESCRIPTION,
                    status = HttpStatusCode.Conflict,
                    placeholders = mapOf("seconds" to creationResult.retryAfterSeconds.toString())
                )
            }

            is MfaCreationResult.Locked -> {
                locale.createError(
                    titleKey = StringResourcesKey.MFA_GENERATION_LOCKED_TITLE,
                    descriptionKey = StringResourcesKey.MFA_GENERATION_LOCKED_DESCRIPTION,
                    status = HttpStatusCode.Forbidden,
                    placeholders = mapOf("minutes" to creationResult.lockDurationMinutes.toString())
                )
            }
        }
    }

    suspend fun updatePassword(
        resetToken: String,
        request: UpdatePasswordRequest,
        locale: Locale
    ): AppResult<UpdatePasswordResponse> {

        // CPU fuera de tx
        val hashedInputToken = passwordResetTokenService.hashToken(resetToken)
        val hashedPassword = hashingService.hash(request.newPassword)
        val now = System.currentTimeMillis()

        val result = runCatching {
            dbExecutor.tx {
                val record = passwordResetTokenRepository.findByToken(hashedInputToken)
                    ?: return@tx UpdatePasswordTxResult.InvalidToken

                if (record.expiresAt < now) {
                    passwordResetTokenRepository.delete(hashedInputToken)
                    return@tx UpdatePasswordTxResult.ExpiredToken
                }

                val user = userRepository.getUserById(record.userId)
                    ?: return@tx UpdatePasswordTxResult.UserNotFound

                val updated = userRepository.updatePassword(user.id, hashedPassword)
                if (!updated) return@tx UpdatePasswordTxResult.UpdateFailed

                // DB: invalidar token usado + limpiar intentos
                passwordResetTokenRepository.delete(hashedInputToken)
                loginAttemptRepository.delete(user.email)

                UpdatePasswordTxResult.Success
            }
        }.getOrElse { error ->
            logger.error("🔥 updatePassword DB error", error)
            return locale.createError(
                StringResourcesKey.PASSWORD_UPDATE_FAILED_TITLE,
                StringResourcesKey.PASSWORD_UPDATE_FAILED_DESCRIPTION,
                status = HttpStatusCode.InternalServerError
            )
        }

        return when (result) {
            UpdatePasswordTxResult.InvalidToken -> locale.createError(
                StringResourcesKey.PASSWORD_RESET_TOKEN_INVALID_TITLE,
                StringResourcesKey.PASSWORD_RESET_TOKEN_INVALID_DESCRIPTION
            )

            UpdatePasswordTxResult.ExpiredToken -> locale.createError(
                StringResourcesKey.PASSWORD_RESET_TOKEN_EXPIRED_TITLE,
                StringResourcesKey.PASSWORD_RESET_TOKEN_EXPIRED_DESCRIPTION
            )

            UpdatePasswordTxResult.UserNotFound -> locale.respondUserNotFoundError()

            UpdatePasswordTxResult.UpdateFailed -> locale.createError(
                StringResourcesKey.PASSWORD_UPDATE_FAILED_TITLE,
                StringResourcesKey.PASSWORD_UPDATE_FAILED_DESCRIPTION,
                status = HttpStatusCode.InternalServerError
            )

            UpdatePasswordTxResult.Success -> AppResult.Success(
                UpdatePasswordResponse(
                    success = true,
                    message = locale.getString(StringResourcesKey.PASSWORD_UPDATE_SUCCESS_MESSAGE)
                )
            )
        }
    }


    suspend fun verifyResetMfa(
        locale: Locale,
        verifyResetMfaRequest: VerifyResetMfaRequest,
    ): AppResult<VerifyResetMfaResponse> {

        val userId = verifyResetMfaRequest.userId

        // CPU fuera de tx
        val hashedInput = hashingService.hashOpaqueToken(verifyResetMfaRequest.code)

        // CPU fuera de tx (token material listo para persistir)
        val tokenData = passwordResetTokenService.generateResetTokenData()

        val result = runCatching {
            dbExecutor.tx {
                val user = userRepository.getUserById(userId)
                    ?: return@tx ResetMfaResult.UserNotFound

                val latestCode = mfaCodeService.getLatestValidMfaCode(
                    userId = user.id,
                    deviceId = null,
                    purpose = MfaPurpose.PASSWORD_RESET
                ) ?: return@tx ResetMfaResult.InvalidCode

                if (hashedInput != latestCode.hashedCode) {
                    return@tx ResetMfaResult.InvalidCode
                }

                // DB: marcar MFA como verificado
                authRepository.completeForgotPasswordMfaVerification(latestCode.id)

                // DB: invalidar tokens anteriores y guardar el nuevo
                passwordResetTokenRepository.deleteByUserId(user.id)
                passwordResetTokenRepository.create(
                    tokenData.hashedToken,
                    user.id,
                    tokenData.expiresAt
                )

                ResetMfaResult.Success(tokenData.plainToken)
            }
        }.getOrElse { error ->
            logger.error("🔥 verifyResetMfa DB error userId=$userId", error)
            return locale.createError(
                StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY
            )
        }

        return when (result) {
            ResetMfaResult.UserNotFound -> locale.respondUserNotFoundError()
            ResetMfaResult.InvalidCode -> locale.respondInvalidMfaCodeError()
            is ResetMfaResult.Success -> AppResult.Success(VerifyResetMfaResponse(result.resetToken))
        }
    }


    private sealed interface ResetMfaResult {
        data object UserNotFound : ResetMfaResult
        data object InvalidCode : ResetMfaResult
        data class Success(val resetToken: String) : ResetMfaResult
    }

    private fun Locale.respondUserNotFoundError(): AppResult.Failure =
        createError(
            StringResourcesKey.AUTH_USER_NOT_FOUND_TITLE,
            StringResourcesKey.AUTH_USER_NOT_FOUND_DESCRIPTION,
            status = HttpStatusCode.NotFound
        )

    private fun Locale.respondInvalidMfaCodeError(): AppResult.Failure =
        createError(
            titleKey = StringResourcesKey.MFA_CODE_INVALID_TITLE,
            descriptionKey = StringResourcesKey.MFA_CODE_INVALID_DESCRIPTION,
            status = HttpStatusCode.Unauthorized
        )

    private fun Locale.respondSignInBlockedUserError(): AppResult.Failure =
        createError(
            StringResourcesKey.AUTH_SIGN_IN_BLOCKED_TITLE,
            StringResourcesKey.AUTH_SIGN_IN_BLOCKED_DESCRIPTION,
            status = HttpStatusCode.Forbidden,
            errorCode = ErrorCode.AUTH_USER_BLOCKED
        )

    private fun Locale.respondUserNotVerifiedError(): AppResult.Failure =
        createError(
            StringResourcesKey.AUTH_USER_NOT_VERIFIED_TITLE,
            StringResourcesKey.AUTH_USER_NOT_VERIFIED_DESCRIPTION,
            status = HttpStatusCode.Forbidden,
            errorCode = ErrorCode.AUTH_USER_NOT_VERIFIED
        )
}
