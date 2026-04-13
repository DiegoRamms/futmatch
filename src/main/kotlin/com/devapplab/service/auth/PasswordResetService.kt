package com.devapplab.service.auth

import com.devapplab.data.database.executor.DbExecutor
import com.devapplab.data.repository.auth.AuthRepository
import com.devapplab.data.repository.login_attempt.LoginAttemptRepository
import com.devapplab.data.repository.password_reset.PasswordResetTokenRepository
import com.devapplab.data.repository.password_reset.PasswordResetVerifyAttemptRepository
import com.devapplab.data.repository.user.UserRepository
import com.devapplab.model.AppResult
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
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

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
    private val passwordResetVerifyAttemptRepository: PasswordResetVerifyAttemptRepository,
    private val mfaRateLimitConfig: MfaRateLimitConfig
) {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private object VerifyResetAttemptPolicy {
        const val MAX_ATTEMPTS_TIER_1 = 5
        const val LOCKOUT_DURATION_TIER_1_MINUTES = 10L
        const val MAX_ATTEMPTS_TIER_2 = 6
        const val LOCKOUT_DURATION_TIER_2_MINUTES = 30L
        const val MAX_ATTEMPTS_TIER_3 = 7
        const val LOCKOUT_DURATION_TIER_3_HOURS = 2L
    }

    suspend fun forgotPassword(
        locale: Locale,
        forgotPasswordRequest: ForgotPasswordRequest
    ): AppResult<ForgotPasswordResponse> {

        val email = forgotPasswordRequest.email.trim()
        val genericSuccessResponse = AppResult.Success(
            ForgotPasswordResponse(
                newCodeSent = true,
                expiresInSeconds = 300,
                resendCodeTimeInSeconds = mfaRateLimitConfig.minWaitSeconds
            )
        )

        val user = runCatching {
            dbExecutor.tx { userRepository.findByEmail(email) }
        }.getOrElse { error ->
            logger.error("🔥 forgotPassword getUser DB error email=$email", error)
            return genericSuccessResponse
        } ?: return genericSuccessResponse

        if (user.status == UserStatus.BLOCKED) {
            return genericSuccessResponse
        }
        if (!user.isEmailVerified) {
            return genericSuccessResponse
        }

        val code = MfaUtils.generateCode()
        val expiresAt = MfaUtils.calculateExpiration(300)
        val hashedMfaCode = hashingService.hashOpaqueToken(code)

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
            return genericSuccessResponse
        }

        when (creationResult) {
            is MfaCreationResult.Created -> {
                runCatching {
                    emailService.sendMfaPasswordResetEmail(user.email, code, locale)
                    logger.info("✅ Sent password reset code to user ${user.id}")
                    runCatching {
                        passwordResetVerifyAttemptRepository.deleteSafe(email)
                    }.onFailure { error ->
                        logger.warn("⚠️ Failed to clear password reset verify attempts after resend | email=$email", error)
                    }
                }.getOrElse { error ->
                    logger.error("📧 Failed to send password reset email to userId=${user.id}", error)
                }
            }

            is MfaCreationResult.Cooldown -> logger.info("ℹ️ forgotPassword cooldown for email=$email")
            is MfaCreationResult.Locked -> logger.info("ℹ️ forgotPassword locked window for email=$email")
        }

        return genericSuccessResponse
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

        val email = verifyResetMfaRequest.email.trim()

        val hashedInput = hashingService.hashOpaqueToken(verifyResetMfaRequest.code)

        val tokenData = passwordResetTokenService.generateResetTokenData()

        val result = runCatching {
            dbExecutor.tx {
                val now = System.currentTimeMillis()
                val attempt = passwordResetVerifyAttemptRepository.findByEmail(email)
                if (attempt?.lockedUntil != null && attempt.lockedUntil > now) {
                    return@tx ResetMfaResult.Locked
                }

                val user = userRepository.findByEmail(email)
                    ?: return@tx registerInvalidResetMfaAttempt(email, now)

                val validCode = mfaCodeService.getValidMfaCodeWithGrace(
                    userId = user.id,
                    deviceId = null,
                    purpose = MfaPurpose.PASSWORD_RESET,
                    hashedInput = hashedInput
                ) ?: return@tx registerInvalidResetMfaAttempt(email, now)

                // DB: marcar MFA como verificado
                authRepository.completeForgotPasswordMfaVerification(validCode.id)

                // DB: invalidar tokens anteriores y guardar el nuevo
                passwordResetTokenRepository.deleteByUserId(user.id)
                passwordResetTokenRepository.create(
                    tokenData.hashedToken,
                    user.id,
                    tokenData.expiresAt
                )
                passwordResetVerifyAttemptRepository.delete(email)

                ResetMfaResult.Success(tokenData.plainToken)
            }
        }.getOrElse { error ->
            logger.error("🔥 verifyResetMfa DB error email=$email", error)
            return locale.createError(
                StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY
            )
        }

        return when (result) {
            ResetMfaResult.UserNotFound -> locale.respondInvalidMfaCodeError()
            ResetMfaResult.InvalidCode -> locale.respondInvalidMfaCodeError()
            ResetMfaResult.Locked -> locale.respondInvalidMfaCodeError()
            is ResetMfaResult.Success -> AppResult.Success(VerifyResetMfaResponse(result.resetToken))
        }
    }


    private sealed interface ResetMfaResult {
        data object UserNotFound : ResetMfaResult
        data object InvalidCode : ResetMfaResult
        data object Locked : ResetMfaResult
        data class Success(val resetToken: String) : ResetMfaResult
    }

    private fun registerInvalidResetMfaAttempt(email: String, now: Long): ResetMfaResult {
        val attempt = passwordResetVerifyAttemptRepository.incrementAttempt(email, now)
        val newAttemptCount = attempt.attempts

        val newLockedUntil: Long? = when {
            newAttemptCount >= VerifyResetAttemptPolicy.MAX_ATTEMPTS_TIER_3 ->
                now + VerifyResetAttemptPolicy.LOCKOUT_DURATION_TIER_3_HOURS.hours.inWholeMilliseconds

            newAttemptCount >= VerifyResetAttemptPolicy.MAX_ATTEMPTS_TIER_2 ->
                now + VerifyResetAttemptPolicy.LOCKOUT_DURATION_TIER_2_MINUTES.minutes.inWholeMilliseconds

            newAttemptCount >= VerifyResetAttemptPolicy.MAX_ATTEMPTS_TIER_1 ->
                now + VerifyResetAttemptPolicy.LOCKOUT_DURATION_TIER_1_MINUTES.minutes.inWholeMilliseconds

            else -> null
        }

        if (newLockedUntil != null) {
            passwordResetVerifyAttemptRepository.updateLockoutIfLater(email, newLockedUntil)
            return ResetMfaResult.Locked
        }

        return ResetMfaResult.InvalidCode
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
}
