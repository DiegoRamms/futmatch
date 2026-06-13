package com.devapplab.service.auth

import com.devapplab.data.database.executor.DbExecutor
import com.devapplab.data.repository.auth.AuthRepository
import com.devapplab.data.repository.device.DeviceRepository
import com.devapplab.data.repository.login_attempt.LoginAttemptRepository
import com.devapplab.data.repository.mfa.LoginMfaChallengeRepository
import com.devapplab.data.repository.mfa.LoginMfaVerifyAttemptRepository
import com.devapplab.data.repository.user.UserRepository
import com.devapplab.model.AppResult
import com.devapplab.model.ErrorCode
import com.devapplab.model.auth.JWTConfig
import com.devapplab.model.auth.request.SignInRequest
import com.devapplab.model.auth.response.AuthCode
import com.devapplab.model.auth.response.AuthResponse
import com.devapplab.model.auth.response.SignOutResponse
import com.devapplab.model.mfa.*
import com.devapplab.model.mfa.response.MfaSendCodeResponse
import com.devapplab.model.user.UserStatus
import com.devapplab.observability.AuthLogSeverity
import com.devapplab.observability.AuthRequestContext
import com.devapplab.observability.authBlocked
import com.devapplab.observability.authFailure
import com.devapplab.observability.authMfaRequired
import com.devapplab.observability.authRejected
import com.devapplab.observability.authSuccess
import com.devapplab.observability.authEvent
import com.devapplab.service.auth.mfa.MfaCodeService
import com.devapplab.service.auth.mfa.LoginMfaChallengeTokenService
import com.devapplab.service.auth.mfa.MfaRateLimitConfig
import com.devapplab.service.auth.state.SignInDeviceDecision
import com.devapplab.service.auth.state.SignInPreCheck
import com.devapplab.service.email.EmailService
import com.devapplab.service.hashing.HashingService
import com.devapplab.utils.MfaUtils
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.createError
import com.devapplab.utils.getString
import io.ktor.http.*
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private data class LoginMfaContext(
    val userId: UUID,
    val deviceId: UUID,
    val challengeTokenHash: String? = null
)

class SignInService(
    private val dbExecutor: DbExecutor,
    private val userRepository: UserRepository,
    private val hashingService: HashingService,
    private val deviceRepository: DeviceRepository,
    private val mfaCodeService: MfaCodeService,
    private val emailService: EmailService,
    private val authRepository: AuthRepository,
    private val loginAttemptRepository: LoginAttemptRepository,
    private val loginMfaChallengeRepository: LoginMfaChallengeRepository,
    private val loginMfaVerifyAttemptRepository: LoginMfaVerifyAttemptRepository,
    private val loginMfaChallengeTokenService: LoginMfaChallengeTokenService,
    private val mfaRateLimitConfig: MfaRateLimitConfig,
    private val authenticatedResponseGenerator: AuthenticatedResponseGenerator,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private object LoginAttemptPolicy {
        const val MAX_ATTEMPTS_TIER_1 = 5
        const val LOCKOUT_DURATION_TIER_1_MINUTES = 10L
        const val MAX_ATTEMPTS_TIER_2 = 6
        const val LOCKOUT_DURATION_TIER_2_MINUTES = 30L
        const val MAX_ATTEMPTS_TIER_3 = 7
        const val LOCKOUT_DURATION_TIER_3_HOURS = 2L
    }

    private object LoginMfaVerifyAttemptPolicy {
        const val MAX_ATTEMPTS_TIER_1 = 5
        const val LOCKOUT_DURATION_TIER_1_MINUTES = 10L
        const val MAX_ATTEMPTS_TIER_2 = 6
        const val LOCKOUT_DURATION_TIER_2_MINUTES = 30L
        const val MAX_ATTEMPTS_TIER_3 = 7
        const val LOCKOUT_DURATION_TIER_3_HOURS = 2L
    }

    suspend fun signIn(
        locale: Locale,
        signInRequest: SignInRequest,
        jwtConfig: JWTConfig,
        deviceInfo: String?,
        context: AuthRequestContext,
    ): AppResult<AuthResponse> {
        val startTime = System.currentTimeMillis()
        val email = signInRequest.email

        if (deviceInfo.isNullOrBlank()) {
            logger.authRejected("auth.sign_in.failed", context, "missing_device_info", statusCode = HttpStatusCode.BadRequest.value, durationMs = System.currentTimeMillis() - startTime)
            return locale.respondDeviceInfoRequired()
        }

        // 1) Lockout + get user (DB read) dentro de tx
        val preCheck = runCatching {
            dbExecutor.tx {
                val attempt = loginAttemptRepository.findByEmail(email)
                val now = System.currentTimeMillis()

                if (attempt?.lockedUntil != null && attempt.lockedUntil > now) {
                    val remainingMinutes = ((attempt.lockedUntil - now) / 60000).coerceAtLeast(1)
                    return@tx SignInPreCheck.Locked(remainingMinutes)
                }

                val user = userRepository.getUserSignInInfo(email)
                SignInPreCheck.Ok(user)
            }
        }.getOrElse { error ->
            logger.authFailure("auth.sign_in.failed", context, "db_error", durationMs = System.currentTimeMillis() - startTime, throwable = error)
            return locale.createError(
                StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY
            )
        }

        if (preCheck is SignInPreCheck.Locked) {
            logger.authBlocked("auth.sign_in.failed", context, "account_locked", statusCode = HttpStatusCode.Forbidden.value, durationMs = System.currentTimeMillis() - startTime, extra = mapOf("remainingMinutes" to preCheck.remainingMinutes))
            return locale.createError(
                StringResourcesKey.AUTH_ACCOUNT_LOCKED_TITLE,
                StringResourcesKey.AUTH_ACCOUNT_LOCKED_DESCRIPTION,
                status = HttpStatusCode.Forbidden,
                placeholders = mapOf("minutes" to preCheck.remainingMinutes.toString())
            )
        }

        val user = (preCheck as SignInPreCheck.Ok).user

        val credentialsOk = user != null && hashingService.verify(signInRequest.password, user.password)
        if (!credentialsOk) {
            logger.authRejected("auth.sign_in.failed", context, "invalid_credentials", statusCode = HttpStatusCode.Unauthorized.value, durationMs = System.currentTimeMillis() - startTime)
            return handleFailedLoginAttemptTx(email, locale, context)
        }

        runCatching {
            dbExecutor.tx { loginAttemptRepository.delete(email) }
        }.onFailure { error ->
            logger.authFailure("auth.sign_in.failed", context, "db_error", throwable = error)
        }

        return when (user.status) {
            UserStatus.BLOCKED -> {
                logger.authBlocked("auth.sign_in.failed", context, "user_blocked", user.userId, statusCode = HttpStatusCode.Forbidden.value, durationMs = System.currentTimeMillis() - startTime)
                locale.respondSignInBlockedUserError()
            }

            UserStatus.SUSPENDED -> {
                logger.authBlocked("auth.sign_in.failed", context, "user_suspended", user.userId, statusCode = HttpStatusCode.Forbidden.value, durationMs = System.currentTimeMillis() - startTime)
                locale.respondSignInSuspendedUserError()
            }

            UserStatus.ACTIVE -> {
                val providedDeviceId = signInRequest.deviceId

                val deviceDecision = runCatching {
                    dbExecutor.tx {
                        val isKnownDevice = providedDeviceId?.let { deviceRepository.isValidDeviceIdForUser(it, user.userId) } ?: false
                        val isTrustedDevice = providedDeviceId?.let { deviceRepository.isTrustedDeviceIdForUser(it, user.userId) } ?: false

                        val resolvedDeviceId =
                            if (isKnownDevice) {
                                providedDeviceId
                            } else {
                                authRepository.createDevice(user.userId, deviceInfo, isTrusted = false)
                            }

                        val needsMfa = !isKnownDevice || !isTrustedDevice || !user.isEmailVerified

                        SignInDeviceDecision(
                            deviceId = resolvedDeviceId,
                            needsMfa = needsMfa
                        )
                    }
                }.getOrElse { error ->
                    logger.authFailure("auth.sign_in.failed", context, "db_error", user.userId, durationMs = System.currentTimeMillis() - startTime, throwable = error)
                    return locale.createError(
                        StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                        StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY
                    )
                }

                if (deviceDecision.needsMfa) {
                    val challengeToken = runCatching {
                        createLoginMfaChallenge(user.userId, deviceDecision.deviceId)
                    }.getOrElse { error ->
                        logger.authFailure("auth.sign_in.failed", context, "db_error", user.userId, deviceDecision.deviceId, durationMs = System.currentTimeMillis() - startTime, throwable = error)
                        return locale.createError(
                            StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                            StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY
                        )
                    }

                    logger.authMfaRequired(context, if (!user.isEmailVerified) "email_not_verified" else "untrusted_device", user.userId, deviceDecision.deviceId, System.currentTimeMillis() - startTime)
                    return AppResult.Success(
                        AuthResponse(
                            userId = user.userId,
                            deviceId = deviceDecision.deviceId,
                            challengeToken = challengeToken,
                            authCode = AuthCode.SUCCESS_NEED_MFA
                        )
                    )
                }

                val result = authenticatedResponseGenerator.generate(
                    locale = locale,
                    userId = user.userId,
                    deviceId = deviceDecision.deviceId,
                    userRole = user.userRole,
                    jwtConfig = jwtConfig
                )
                if (result is AppResult.Success) {
                    logger.authSuccess("auth.sign_in.success", context, user.userId, deviceDecision.deviceId, durationMs = System.currentTimeMillis() - startTime)
                }
                result
            }
        }
    }

    private suspend fun handleFailedLoginAttemptTx(email: String, locale: Locale, context: AuthRequestContext): AppResult.Failure {
        val lockUntil = runCatching {
            dbExecutor.tx {
                val now = System.currentTimeMillis()
                val attempt = loginAttemptRepository.incrementAttempt(email, now)
                val newAttemptCount = attempt.attempts

                val newLockedUntil: Long? = when {
                    newAttemptCount >= LoginAttemptPolicy.MAX_ATTEMPTS_TIER_3 ->
                        now + LoginAttemptPolicy.LOCKOUT_DURATION_TIER_3_HOURS.hours.inWholeMilliseconds

                    newAttemptCount >= LoginAttemptPolicy.MAX_ATTEMPTS_TIER_2 ->
                        now + LoginAttemptPolicy.LOCKOUT_DURATION_TIER_2_MINUTES.minutes.inWholeMilliseconds

                    newAttemptCount >= LoginAttemptPolicy.MAX_ATTEMPTS_TIER_1 ->
                        now + LoginAttemptPolicy.LOCKOUT_DURATION_TIER_1_MINUTES.minutes.inWholeMilliseconds

                    else -> null
                }

                if (newLockedUntil != null) {
                    loginAttemptRepository.updateLockoutIfLater(email, newLockedUntil)
                }

                loginAttemptRepository.findByEmail(email)?.lockedUntil
            }
        }.getOrElse { error ->
            logger.authFailure("auth.sign_in.failed", context, "db_error", throwable = error)
            return locale.createError(
                StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY
            )
        }

        if (lockUntil != null && lockUntil > System.currentTimeMillis()) {
            val remainingMinutes = ((lockUntil - System.currentTimeMillis()) / 60000).coerceAtLeast(1)
            return locale.createError(
                StringResourcesKey.AUTH_ACCOUNT_LOCKED_TITLE,
                StringResourcesKey.AUTH_ACCOUNT_LOCKED_DESCRIPTION,
                status = HttpStatusCode.Forbidden,
                placeholders = mapOf("minutes" to remainingMinutes.toString())
            )
        }

        return locale.respondInvalidSignInCredentialsError()
    }

    suspend fun sendMFACode(
        locale: Locale,
        mfaCodeRequest: MfaCodeRequest,
        context: AuthRequestContext
    ): AppResult<MfaSendCodeResponse> {
        val mfaContext = resolveLoginMfaContext(
            challengeToken = mfaCodeRequest.challengeToken,
            fallbackUserId = mfaCodeRequest.userId,
            fallbackDeviceId = mfaCodeRequest.deviceId,
            operation = "mfa/send",
            context = context
        ) ?: run {
            logger.authEvent(
                severity = AuthLogSeverity.WARN,
                event = "auth.mfa.challenge.invalid",
                context = context,
                outcome = "rejected",
                reason = "invalid_challenge",
                statusCode = HttpStatusCode.Unauthorized.value
            )
            return locale.respondInvalidMfaCodeError()
        }

        val userId = mfaContext.userId
        val deviceId = mfaContext.deviceId

        val user = runCatching {
            dbExecutor.tx { userRepository.getUserById(userId) }
        }.getOrElse { error ->
            logger.authEvent(
                severity = AuthLogSeverity.ERROR,
                event = "auth.mfa.send.failed",
                context = context,
                outcome = "failed",
                reason = "db_error",
                userId = userId,
                deviceId = deviceId,
                throwable = error
            )
            return locale.createError(
                titleKey = StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                descriptionKey = StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY,
                status = HttpStatusCode.InternalServerError,
                errorCode = ErrorCode.GENERAL_ERROR
            )
        } ?: return locale.respondUserNotFoundError()

        if (user.status == UserStatus.BLOCKED) {
            return locale.respondSignInBlockedUserError()
        }

        val code = MfaUtils.generateCode()
        val expiresAt = MfaUtils.calculateExpiration(300)
        val hashedMfaCode = hashingService.hashOpaqueToken(code)

        val creationResult = runCatching {
            dbExecutor.tx {
                mfaCodeService.createMfaCode(
                    userId = user.id,
                    deviceId = deviceId,
                    hashedCode = hashedMfaCode,
                    channel = MfaChannel.EMAIL,
                    purpose = MfaPurpose.SIGN_IN,
                    expiresAt = expiresAt,
                    config = mfaRateLimitConfig
                )
            }
        }.getOrElse { error ->
            logger.authEvent(
                severity = AuthLogSeverity.ERROR,
                event = "auth.mfa.send.failed",
                context = context,
                outcome = "failed",
                reason = "db_error",
                userId = user.id,
                deviceId = deviceId,
                throwable = error
            )
            return locale.createError(
                titleKey = StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                descriptionKey = StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY,
                status = HttpStatusCode.InternalServerError,
                errorCode = ErrorCode.GENERAL_ERROR
            )
        }

        return when (creationResult) {
            is MfaCreationResult.Created -> {
                val emailSent = runCatching {
                    emailService.sendMfaCodeEmail(user.email, code, locale)
                    true
                }.getOrElse { error ->
                    logger.authEvent(
                        severity = AuthLogSeverity.ERROR,
                        event = "auth.mfa.send.failed",
                        context = context,
                        outcome = "failed",
                        reason = "email_send_failed",
                        userId = user.id,
                        deviceId = deviceId,
                        throwable = error
                    )
                    false
                }

                if (!emailSent) {
                    return locale.createError(
                        titleKey = StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                        descriptionKey = StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY,
                        status = HttpStatusCode.InternalServerError,
                        errorCode = ErrorCode.GENERAL_ERROR
                    )
                }

                logger.authEvent(
                    severity = AuthLogSeverity.INFO,
                    event = "auth.mfa.send.success",
                    context = context,
                    outcome = "success",
                    userId = user.id,
                    deviceId = deviceId,
                    extra = mapOf("purpose" to "SIGN_IN")
                )
                runCatching {
                    loginMfaVerifyAttemptRepository.deleteSafe(user.id, deviceId)
                }.onFailure { error ->
                    logger.authEvent(AuthLogSeverity.WARN, "auth.mfa.send.failed", context, "failed", "cleanup_failed", userId = user.id, deviceId = deviceId, throwable = error)
                }
                AppResult.Success(
                    MfaSendCodeResponse(
                        newCodeSent = true,
                        expiresInSeconds = creationResult.expiresInSeconds,
                        resendCodeTimeInSeconds = mfaRateLimitConfig.minWaitSeconds
                    )
                )
            }

            is MfaCreationResult.Cooldown -> {
                logger.authEvent(
                    severity = AuthLogSeverity.WARN,
                    event = "auth.mfa.send.failed",
                    context = context,
                    outcome = "rejected",
                    reason = "cooldown",
                    userId = user.id,
                    deviceId = deviceId,
                    statusCode = HttpStatusCode.Conflict.value,
                    extra = mapOf("retryAfterSeconds" to creationResult.retryAfterSeconds)
                )
                locale.createError(
                    titleKey = StringResourcesKey.MFA_COOLDOWN_TITLE,
                    descriptionKey = StringResourcesKey.MFA_COOLDOWN_DESCRIPTION,
                    status = HttpStatusCode.Conflict,
                    errorCode = ErrorCode.TOO_MANY_REQUESTS,
                    placeholders = mapOf("seconds" to creationResult.retryAfterSeconds.toString())
                )
            }

            is MfaCreationResult.Locked -> {
                logger.authEvent(
                    severity = AuthLogSeverity.WARN,
                    event = "auth.mfa.send.failed",
                    context = context,
                    outcome = "blocked",
                    reason = "generation_locked",
                    userId = user.id,
                    deviceId = deviceId,
                    statusCode = HttpStatusCode.Forbidden.value,
                    extra = mapOf("lockDurationMinutes" to creationResult.lockDurationMinutes)
                )
                locale.createError(
                    titleKey = StringResourcesKey.MFA_GENERATION_LOCKED_TITLE,
                    descriptionKey = StringResourcesKey.MFA_GENERATION_LOCKED_DESCRIPTION,
                    status = HttpStatusCode.Forbidden,
                    errorCode = ErrorCode.MFA_GENERATION_LOCKED,
                    placeholders = mapOf("minutes" to creationResult.lockDurationMinutes.toString())
                )
            }
        }
    }

    suspend fun verifyMfaCode(
        locale: Locale,
        mfaCodeVerificationRequest: MfaCodeVerificationRequest,
        jwtConfig: JWTConfig,
        context: AuthRequestContext
    ): AppResult<AuthResponse> {
        val mfaContext = resolveLoginMfaContext(
            challengeToken = mfaCodeVerificationRequest.challengeToken,
            fallbackUserId = mfaCodeVerificationRequest.userId,
            fallbackDeviceId = mfaCodeVerificationRequest.deviceId,
            operation = "mfa/verify",
            context = context
        ) ?: run {
            logger.authEvent(
                severity = AuthLogSeverity.WARN,
                event = "auth.mfa.challenge.invalid",
                context = context,
                outcome = "rejected",
                reason = "invalid_challenge",
                statusCode = HttpStatusCode.Unauthorized.value
            )
            return locale.respondInvalidMfaCodeError()
        }
        val userId = mfaContext.userId
        val deviceId = mfaContext.deviceId
        val hashedInput = hashingService.hashOpaqueToken(mfaCodeVerificationRequest.code)

        val userRole = runCatching {
            dbExecutor.tx {
                val now = System.currentTimeMillis()
                val verifyAttempt = loginMfaVerifyAttemptRepository.find(userId, deviceId)
                if (verifyAttempt?.lockedUntil != null && verifyAttempt.lockedUntil > now) {
                    return@tx null
                }

                val validCode = mfaCodeService.getValidMfaCodeWithGrace(
                    userId,
                    deviceId,
                    MfaPurpose.SIGN_IN,
                    hashedInput = hashedInput
                ) ?: return@tx registerInvalidLoginMfaAttempt(userId, deviceId, now)

                val user = userRepository.getUserById(userId)
                    ?: return@tx registerInvalidLoginMfaAttempt(userId, deviceId, now)

                authRepository.completeMfaVerification(userId, deviceId, validCode.id)
                loginMfaVerifyAttemptRepository.delete(userId, deviceId)
                mfaContext.challengeTokenHash?.let { loginMfaChallengeRepository.markUsed(it, now) }

                user.userRole
            }
        }.getOrElse { error ->
            logger.authEvent(
                severity = AuthLogSeverity.ERROR,
                event = "auth.mfa.verify.failed",
                context = context,
                outcome = "failed",
                reason = "db_error",
                userId = userId,
                deviceId = deviceId,
                throwable = error
            )
            return locale.createError(
                StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY
            )
        } ?: run {
            logger.authEvent(
                severity = AuthLogSeverity.WARN,
                event = "auth.mfa.verify.failed",
                context = context,
                outcome = "rejected",
                reason = "invalid_mfa_code",
                userId = userId,
                deviceId = deviceId,
                statusCode = HttpStatusCode.Unauthorized.value
            )
            return locale.respondInvalidMfaCodeError()
        }

        val result = authenticatedResponseGenerator.generate(locale, userId, deviceId, userRole, jwtConfig)
        if (result is AppResult.Success) {
            logger.authEvent(
                severity = AuthLogSeverity.INFO,
                event = "auth.mfa.verify.success",
                context = context,
                outcome = "success",
                userId = userId,
                deviceId = deviceId
            )
        }
        return result
    }

    private suspend fun createLoginMfaChallenge(userId: UUID, deviceId: UUID): String {
        val now = System.currentTimeMillis()
        val tokenData = loginMfaChallengeTokenService.generateChallengeTokenData(now)

        dbExecutor.tx {
            loginMfaChallengeRepository.revokeActiveByUserAndDevice(userId, deviceId, now)
            loginMfaChallengeRepository.create(
                tokenHash = tokenData.hashedToken,
                userId = userId,
                deviceId = deviceId,
                expiresAt = tokenData.expiresAt,
                createdAt = now
            ) ?: error("Failed to create login MFA challenge")
        }

        return tokenData.plainToken
    }

    private suspend fun resolveLoginMfaChallenge(challengeToken: String) =
        dbExecutor.tx {
            loginMfaChallengeRepository.findValidByTokenHash(
                tokenHash = loginMfaChallengeTokenService.hashToken(challengeToken),
                now = System.currentTimeMillis()
            )
        }

    private suspend fun resolveLoginMfaContext(
        challengeToken: String?,
        fallbackUserId: UUID?,
        fallbackDeviceId: UUID?,
        operation: String,
        context: AuthRequestContext
    ): LoginMfaContext? {
        if (!challengeToken.isNullOrBlank()) {
            val challenge = resolveLoginMfaChallenge(challengeToken) ?: return null
            return LoginMfaContext(
                userId = challenge.userId,
                deviceId = challenge.deviceId,
                challengeTokenHash = challenge.tokenHash
            )
        }

        // TODO: Remove legacy userId/deviceId MFA payload support once clients are migrated to challengeToken.
        if (fallbackUserId != null && fallbackDeviceId != null) {
            logger.authEvent(
                severity = AuthLogSeverity.WARN,
                event = "auth.mfa.legacy_fallback",
                context = context,
                outcome = "rejected",
                reason = "legacy_payload_deprecated",
                userId = fallbackUserId,
                deviceId = fallbackDeviceId,
                extra = mapOf("operation" to operation)
            )
            return LoginMfaContext(
                userId = fallbackUserId,
                deviceId = fallbackDeviceId
            )
        }

        return null
    }

    private fun registerInvalidLoginMfaAttempt(userId: UUID, deviceId: UUID, now: Long): com.devapplab.model.user.UserRole? {
        val attempt = loginMfaVerifyAttemptRepository.incrementAttempt(userId, deviceId, now)
        val newAttemptCount = attempt.attempts

        val newLockedUntil: Long? = when {
            newAttemptCount >= LoginMfaVerifyAttemptPolicy.MAX_ATTEMPTS_TIER_3 ->
                now + LoginMfaVerifyAttemptPolicy.LOCKOUT_DURATION_TIER_3_HOURS.hours.inWholeMilliseconds

            newAttemptCount >= LoginMfaVerifyAttemptPolicy.MAX_ATTEMPTS_TIER_2 ->
                now + LoginMfaVerifyAttemptPolicy.LOCKOUT_DURATION_TIER_2_MINUTES.minutes.inWholeMilliseconds

            newAttemptCount >= LoginMfaVerifyAttemptPolicy.MAX_ATTEMPTS_TIER_1 ->
                now + LoginMfaVerifyAttemptPolicy.LOCKOUT_DURATION_TIER_1_MINUTES.minutes.inWholeMilliseconds

            else -> null
        }

        if (newLockedUntil != null) {
            loginMfaVerifyAttemptRepository.updateLockoutIfLater(userId, deviceId, newLockedUntil)
        }

        return null
    }

    suspend fun signOut(
        locale: Locale,
        userId: UUID,
        deviceId: UUID,
        context: AuthRequestContext
    ): AppResult<SignOutResponse> {
        val isOwnedByUser = dbExecutor.tx {
            deviceRepository.isValidDeviceIdForUser(deviceId, userId)
        }

        if (!isOwnedByUser) {
            logger.authEvent(
                severity = AuthLogSeverity.WARN,
                event = "auth.sign_out.failed",
                context = context,
                outcome = "rejected",
                reason = "device_not_owned",
                userId = userId,
                deviceId = deviceId,
                statusCode = HttpStatusCode.Forbidden.value
            )
            return locale.respondSignOutAccessDeniedError()
        }

        val wasRevoke = dbExecutor.tx {
            authRepository.revokeRefreshToken(deviceId)
        }

        return if (wasRevoke) {
            logger.authEvent(
                severity = AuthLogSeverity.INFO,
                event = "auth.sign_out.success",
                context = context,
                outcome = "success",
                userId = userId,
                deviceId = deviceId
            )
            AppResult.Success(
                SignOutResponse(
                    success = true,
                    message = locale.getString(StringResourcesKey.AUTH_SIGN_OUT_SUCCESS_MESSAGE)
                )
            )
        } else {
            logger.authEvent(
                severity = AuthLogSeverity.ERROR,
                event = "auth.sign_out.failed",
                context = context,
                outcome = "failed",
                reason = "revoke_failed",
                userId = userId,
                deviceId = deviceId,
                statusCode = HttpStatusCode.InternalServerError.value
            )
            locale.respondSignOutError()
        }
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

    private fun Locale.respondDeviceInfoRequired(): AppResult.Failure = createError(
        StringResourcesKey.AUTH_DEVICE_INFO_REQUIRED_TITLE,
        StringResourcesKey.AUTH_DEVICE_INFO_REQUIRED_DESCRIPTION,
        status = HttpStatusCode.BadRequest
    )

    private fun Locale.respondInvalidSignInCredentialsError(): AppResult.Failure =
        createError(
            StringResourcesKey.AUTH_INVALID_SIGN_IN_TITLE,
            StringResourcesKey.AUTH_INVALID_SIGN_IN_DESCRIPTION,
            errorCode = ErrorCode.GENERAL_ERROR,
            status = HttpStatusCode.Unauthorized
        )

    private fun Locale.respondSignInBlockedUserError(): AppResult.Failure =
        createError(
            StringResourcesKey.AUTH_SIGN_IN_BLOCKED_TITLE,
            StringResourcesKey.AUTH_SIGN_IN_BLOCKED_DESCRIPTION,
            status = HttpStatusCode.Forbidden,
            errorCode = ErrorCode.AUTH_USER_BLOCKED
        )

    private fun Locale.respondSignInSuspendedUserError(): AppResult.Failure =
        createError(
            StringResourcesKey.AUTH_SIGN_IN_SUSPENDED_TITLE,
            StringResourcesKey.AUTH_SIGN_IN_SUSPENDED_DESCRIPTION,
            status = HttpStatusCode.Forbidden,
            errorCode = ErrorCode.AUTH_USER_SUSPENDED
        )
    
    private fun Locale.respondSignOutError(): AppResult.Failure = createError(
        StringResourcesKey.AUTH_SIGN_OUT_FAILED_TITLE,
        StringResourcesKey.AUTH_SIGN_OUT_FAILED_DESCRIPTION,
        status = HttpStatusCode.InternalServerError,
        errorCode = ErrorCode.GENERAL_ERROR
    )

    private fun Locale.respondSignOutAccessDeniedError(): AppResult.Failure = createError(
        StringResourcesKey.ACCESS_DENIED_TITLE,
        StringResourcesKey.ACCESS_DENIED_DESCRIPTION,
        status = HttpStatusCode.Forbidden,
        errorCode = ErrorCode.ACCESS_DENIED
    )
}
