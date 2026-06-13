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
    ): AppResult<AuthResponse> {
        val startTime = System.currentTimeMillis()
        val email = signInRequest.email

        if (deviceInfo.isNullOrBlank()) {
            logger.error("❌ signIn - Missing device info (Took ${System.currentTimeMillis() - startTime} ms)")
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
            logger.error("🔥 signIn - DB error on preCheck for email=$email", error)
            return locale.createError(
                StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY
            )
        }

        if (preCheck is SignInPreCheck.Locked) {
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
            logger.error("❌ signIn - Invalid credentials (Took ${System.currentTimeMillis() - startTime} ms)")
            return handleFailedLoginAttemptTx(email, locale)
        }

        runCatching {
            dbExecutor.tx { loginAttemptRepository.delete(email) }
        }.onFailure { error ->
            logger.error("🔥 signIn - Failed to clear login attempts", error)
        }

        return when (user.status) {
            UserStatus.BLOCKED -> {
                logger.error("❌ signIn - User is blocked (Took ${System.currentTimeMillis() - startTime} ms)")
                locale.respondSignInBlockedUserError()
            }

            UserStatus.SUSPENDED -> {
                logger.error("❌ signIn - User is suspended (Took ${System.currentTimeMillis() - startTime} ms)")
                locale.respondSignInSuspendedUserError()
            }

            UserStatus.ACTIVE -> {
                logger.info("✅ signIn - Credentials verified (Took ${System.currentTimeMillis() - startTime} ms)")

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
                    logger.error("🔥 signIn - Device resolution failed for user=${user.userId}", error)
                    return locale.createError(
                        StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                        StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY
                    )
                }

                if (deviceDecision.needsMfa) {
                    val challengeToken = runCatching {
                        createLoginMfaChallenge(user.userId, deviceDecision.deviceId)
                    }.getOrElse { error ->
                        logger.error("🔥 signIn - Challenge creation failed for user=${user.userId}", error)
                        return locale.createError(
                            StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                            StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY
                        )
                    }

                    return AppResult.Success(
                        AuthResponse(
                            challengeToken = challengeToken,
                            authCode = AuthCode.SUCCESS_NEED_MFA
                        )
                    )
                }

                authenticatedResponseGenerator.generate(
                    locale = locale,
                    userId = user.userId,
                    deviceId = deviceDecision.deviceId,
                    userRole = user.userRole,
                    jwtConfig = jwtConfig
                )
            }
        }
    }

    private suspend fun handleFailedLoginAttemptTx(email: String, locale: Locale): AppResult.Failure {
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
            logger.error("🔥 signIn - Failed to update login attempts for email=$email", error)
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
        mfaCodeRequest: MfaCodeRequest
    ): AppResult<MfaSendCodeResponse> {
        val mfaContext = resolveLoginMfaContext(
            challengeToken = mfaCodeRequest.challengeToken,
            fallbackUserId = mfaCodeRequest.userId,
            fallbackDeviceId = mfaCodeRequest.deviceId,
            operation = "mfa/send"
        ) ?: return locale.respondInvalidMfaCodeError()

        val userId = mfaContext.userId
        val deviceId = mfaContext.deviceId

        val user = runCatching {
            dbExecutor.tx { userRepository.getUserById(userId) }
        }.getOrElse { error ->
            logger.error("🔥 sendMFACode getUserById DB error userId=$userId", error)
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
            logger.error("🔥 sendMFACode createMfaCode DB error userId=${user.id}", error)
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
                    logger.error("📧 Failed to send MFA email userId=${user.id} email=${user.email}", error)
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

                logger.info("✅ Sent MFA code to user ${user.id} for purpose SIGN_IN")
                runCatching {
                    loginMfaVerifyAttemptRepository.deleteSafe(user.id, deviceId)
                }.onFailure { error ->
                    logger.warn("⚠️ Failed to clear login MFA verify attempts after sending code | userId=${user.id} deviceId=$deviceId", error)
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
                locale.createError(
                    titleKey = StringResourcesKey.MFA_COOLDOWN_TITLE,
                    descriptionKey = StringResourcesKey.MFA_COOLDOWN_DESCRIPTION,
                    status = HttpStatusCode.Conflict,
                    errorCode = ErrorCode.TOO_MANY_REQUESTS,
                    placeholders = mapOf("seconds" to creationResult.retryAfterSeconds.toString())
                )
            }

            is MfaCreationResult.Locked -> {
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
        jwtConfig: JWTConfig
    ): AppResult<AuthResponse> {
        val mfaContext = resolveLoginMfaContext(
            challengeToken = mfaCodeVerificationRequest.challengeToken,
            fallbackUserId = mfaCodeVerificationRequest.userId,
            fallbackDeviceId = mfaCodeVerificationRequest.deviceId,
            operation = "mfa/verify"
        ) ?: return locale.respondInvalidMfaCodeError()
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
            logger.error("🔥 verifyMfaCode DB error userId=$userId deviceId=$deviceId", error)
            return locale.createError(
                StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY
            )
        } ?: return locale.respondInvalidMfaCodeError()

        return authenticatedResponseGenerator.generate(locale, userId, deviceId, userRole, jwtConfig)
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
        operation: String
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
            logger.warn(
                "Deprecated legacy login MFA payload used on {}. Prefer challengeToken-based flow. userId={} deviceId={}",
                operation,
                fallbackUserId,
                fallbackDeviceId
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

    suspend fun signOut(locale: Locale, userId: UUID, deviceId: UUID): AppResult<SignOutResponse> {
        val isOwnedByUser = dbExecutor.tx {
            deviceRepository.isValidDeviceIdForUser(deviceId, userId)
        }

        if (!isOwnedByUser) {
            logger.warn("⚠️ signOut denied. deviceId=$deviceId does not belong to userId=$userId")
            return locale.respondSignOutAccessDeniedError()
        }

        val wasRevoke = dbExecutor.tx {
            authRepository.revokeRefreshToken(deviceId)
        }

        return if (wasRevoke) {
            AppResult.Success(
                SignOutResponse(
                    success = true,
                    message = locale.getString(StringResourcesKey.AUTH_SIGN_OUT_SUCCESS_MESSAGE)
                )
            )
        } else locale.respondSignOutError()
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
