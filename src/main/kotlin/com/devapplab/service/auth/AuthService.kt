package com.devapplab.service.auth

import com.devapplab.data.database.executor.DbExecutor
import com.devapplab.data.repository.RefreshTokenRepository
import com.devapplab.data.repository.auth.AuthRepository
import com.devapplab.data.repository.device.DeviceRepository
import com.devapplab.data.repository.login_attempt.LoginAttemptRepository
import com.devapplab.data.repository.password_reset.PasswordResetTokenRepository
import com.devapplab.data.repository.pending_registrations.PendingRegistrationRepository
import com.devapplab.data.repository.user.UserRepository
import com.devapplab.model.AppResult
import com.devapplab.model.ErrorCode
import com.devapplab.model.auth.*
import com.devapplab.model.auth.request.*
import com.devapplab.model.auth.response.*
import com.devapplab.model.mfa.*
import com.devapplab.model.mfa.response.MfaSendCodeResponse
import com.devapplab.model.user.PendingUser
import com.devapplab.model.user.UserRole
import com.devapplab.model.user.UserStatus
import com.devapplab.model.user.request.UpdatePasswordRequest
import com.devapplab.model.user.response.UpdatePasswordResponse
import com.devapplab.service.auth.state.CompleteRegistrationAbort
import com.devapplab.service.auth.state.CompleteRegistrationFailure
import com.devapplab.service.auth.state.ResendRegistrationCodeDecision
import com.devapplab.utils.MfaUtils
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.createError
import com.devapplab.utils.getString
import io.ktor.http.*
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


class AuthService(
    private val dbExecutor: DbExecutor,
    private val userRepository: UserRepository,
    private val hashingService: com.devapplab.service.hashing.HashingService,
    private val authTokenService: com.devapplab.service.auth.auth_token.AuthTokenService,
    private val refreshTokenService: com.devapplab.service.auth.refresh_token.RefreshTokenService,
    private val passwordResetTokenService: com.devapplab.service.password_reset.PasswordResetTokenService,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val deviceRepository: DeviceRepository,
    private val mfaCodeService: com.devapplab.service.auth.mfa.MfaCodeService,
    private val emailService: com.devapplab.service.email.EmailService,
    private val authRepository: AuthRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val loginAttemptRepository: LoginAttemptRepository,
    private val pendingRegistrationRepository: PendingRegistrationRepository,
    private val mfaRateLimitConfig: com.devapplab.service.auth.mfa.MfaRateLimitConfig
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    private object LoginAttemptPolicy {
        const val MAX_ATTEMPTS_TIER_1 = 5
        const val LOCKOUT_DURATION_TIER_1_MINUTES = 15L
        const val MAX_ATTEMPTS_TIER_2 = 6
        const val LOCKOUT_DURATION_TIER_2_HOURS = 1L
        const val MAX_ATTEMPTS_TIER_3 = 7
        const val LOCKOUT_DURATION_TIER_3_HOURS = 24L
    }

    private object RegistrationPolicy {
        val EXPIRATION_DURATION = 1.hours
        val RESEND_COOLDOWN = 60.seconds
    }

    suspend fun startRegistration(
        request: RegisterUserRequest,
        locale: Locale
    ): AppResult<SimpleResponse> {

        val email = request.email
        val now = System.currentTimeMillis()

        val verificationCode = MfaUtils.generateCode()
        val hashedCode = hashingService.hashOpaqueToken(verificationCode)
        val expiresAt = now + RegistrationPolicy.EXPIRATION_DURATION.inWholeMilliseconds
        val hashedPassword = hashingService.hash(request.password)
        val requestWithHashedPassword = request.copy(password = hashedPassword)

        val shouldSendEmail = runCatching {
            dbExecutor.tx {
                val alreadyRegistered = userRepository.isEmailAlreadyRegistered(email)
                if (alreadyRegistered) {
                    logger.warn("⚠️ Registration attempt for already existing user: $email")
                    return@tx false
                }

                pendingRegistrationRepository.findByEmail(email)?.let {
                    pendingRegistrationRepository.delete(it.id)
                }

                pendingRegistrationRepository.create(
                    request = requestWithHashedPassword,
                    hashedVerificationCode = hashedCode,
                    expiresAt = expiresAt
                )

                true
            }
        }.getOrElse { error ->
            logger.error("🔥 startRegistration DB error for email=$email (responding success anyway)", error)
            false
        }

        if (shouldSendEmail) {
            runCatching {
                emailService.sendRegistrationEmail(email, verificationCode, locale)
                logger.info("✅ Sent registration verification code to $email")
            }.onFailure { error ->
                logger.error("📧 Failed to send registration email to $email", error)
            }
        }

        return AppResult.Success(
            SimpleResponse(
                success = true,
                message = locale.getString(StringResourcesKey.REGISTRATION_EMAIL_SENT_MESSAGE),
                resendCodeTimeInSeconds = RegistrationPolicy.RESEND_COOLDOWN.inWholeSeconds
            )
        )
    }


    suspend fun completeRegistration(
        request: CompleteRegistrationRequest,
        jwtConfig: JWTConfig,
        locale: Locale,
        deviceInfo: String?
    ): AppResult<AuthResponse> {

        if (deviceInfo.isNullOrBlank()) {
            logger.error("❌ completeRegistration - Missing device info")
            return locale.respondDeviceInfoRequired()
        }

        val now = System.currentTimeMillis()
        val hashedCode = hashingService.hashOpaqueToken(request.verificationCode)

        val txResult = runCatching {
            dbExecutor.tx {
                val pendingRegistration = pendingRegistrationRepository.findByEmail(request.email)
                    ?: throw CompleteRegistrationAbort(CompleteRegistrationFailure.PendingNotFound)

                if (pendingRegistration.expiresAt < now) {
                    pendingRegistrationRepository.delete(pendingRegistration.id)
                    throw CompleteRegistrationAbort(CompleteRegistrationFailure.Expired)
                }

                if (hashedCode != pendingRegistration.verificationCode) {
                    throw CompleteRegistrationAbort(CompleteRegistrationFailure.InvalidCode)
                }

                val pendingUserToCreate = PendingUser(
                    name = pendingRegistration.name,
                    lastName = pendingRegistration.lastName,
                    email = pendingRegistration.email,
                    password = pendingRegistration.password,
                    phone = pendingRegistration.phone,
                    country = pendingRegistration.country,
                    birthDate = pendingRegistration.birthDate,
                    playerPosition = pendingRegistration.playerPosition,
                    gender = pendingRegistration.gender,
                    profilePic = pendingRegistration.profilePic,
                    level = pendingRegistration.level,
                    userRole = pendingRegistration.userRole,
                    isEmailVerified = true,
                    status = UserStatus.ACTIVE,
                    createdAt = now,
                    updatedAt = now
                )

                val savedUser = userRepository.create(pendingUserToCreate)

                val userId = savedUser.id
                    ?: throw CompleteRegistrationAbort(CompleteRegistrationFailure.MissingUserId)

                pendingRegistrationRepository.delete(pendingRegistration.id)
                loginAttemptRepository.delete(savedUser.email)

                val deviceId = authRepository.createDevice(
                    userId = userId,
                    deviceInfo = deviceInfo,
                    isTrusted = true
                )

                Triple(savedUser, deviceId, userId)
            }
        }.getOrElse { error ->
            logger.error("❌ completeRegistration failed", error)

            return when (error) {
                is CompleteRegistrationAbort -> when (error.reason) {
                    CompleteRegistrationFailure.PendingNotFound,
                    CompleteRegistrationFailure.InvalidCode -> locale.createError(
                        StringResourcesKey.REGISTRATION_CODE_INVALID_TITLE,
                        StringResourcesKey.REGISTRATION_CODE_INVALID_DESCRIPTION
                    )

                    CompleteRegistrationFailure.Expired -> locale.createError(
                        StringResourcesKey.REGISTRATION_CODE_EXPIRED_TITLE,
                        StringResourcesKey.REGISTRATION_CODE_EXPIRED_DESCRIPTION
                    )

                    CompleteRegistrationFailure.CreateUserFailed,
                    CompleteRegistrationFailure.MissingUserId -> locale.createError(
                        StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                        StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY
                    )
                }

                else -> locale.createError(
                    StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                    StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY
                )
            }
        }

        val (savedUser, deviceId, userId) = txResult

        return generateAuthenticatedResponse(
            locale = locale,
            userId = userId,
            deviceId = deviceId,
            userRole = savedUser.role,
            jwtConfig = jwtConfig
        )
    }

    suspend fun resendRegistrationCode(
        request: ResendRegistrationCodeRequest,
        locale: Locale
    ): AppResult<SimpleResponse> {

        val email = request.email
        val now = System.currentTimeMillis()

        val newVerificationCode = MfaUtils.generateCode()
        val newHashedCode = hashingService.hashOpaqueToken(newVerificationCode)
        val newExpiresAt = now + RegistrationPolicy.EXPIRATION_DURATION.inWholeMilliseconds

        val shouldSendEmail = runCatching {
            dbExecutor.tx {
                val pending = pendingRegistrationRepository.findByEmail(email)
                    ?: return@tx ResendRegistrationCodeDecision.NotFoundOrExpired


                if (pending.expiresAt < now) {
                    return@tx ResendRegistrationCodeDecision.NotFoundOrExpired
                }

                val timeSinceLastUpdate = now - pending.updatedAt
                val cooldownMs = RegistrationPolicy.RESEND_COOLDOWN.inWholeMilliseconds

                if (timeSinceLastUpdate < cooldownMs) {
                    val remainingSeconds = ((cooldownMs - timeSinceLastUpdate) / 1000).coerceAtLeast(1)
                    return@tx ResendRegistrationCodeDecision.Cooldown(remainingSeconds)
                }


                val updated = pendingRegistrationRepository.updateCodeAndExpiration(
                    id = pending.id,
                    newCode = newHashedCode,
                    newExpiresAt = newExpiresAt,
                    newUpdatedAt = now
                )

                if (!updated) {
                    return@tx ResendRegistrationCodeDecision.DbUpdateFailed
                }

                ResendRegistrationCodeDecision.SendEmail
            }
        }.getOrElse { error ->
            logger.error("🔥 resendRegistrationCode DB error for email=$email", error)
            return locale.createError(
                StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY
            )
        }

        when (shouldSendEmail) {
            ResendRegistrationCodeDecision.NotFoundOrExpired -> {
                return locale.createError(
                    StringResourcesKey.REGISTRATION_NOT_FOUND_OR_EXPIRED_TITLE,
                    StringResourcesKey.REGISTRATION_NOT_FOUND_OR_EXPIRED_DESCRIPTION
                )
            }

            is ResendRegistrationCodeDecision.Cooldown -> {
                return locale.createError(
                    StringResourcesKey.REGISTRATION_RESEND_COOLDOWN_TITLE,
                    StringResourcesKey.REGISTRATION_RESEND_COOLDOWN_DESCRIPTION,
                    placeholders = mapOf("seconds" to shouldSendEmail.remainingSeconds.toString())
                )
            }

            ResendRegistrationCodeDecision.DbUpdateFailed -> {
                logger.error("Failed to update pending registration code for email: $email")
                return locale.createError(
                    StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                    StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY
                )
            }

            ResendRegistrationCodeDecision.SendEmail -> {
                return runCatching {
                    emailService.sendRegistrationEmail(email, newVerificationCode, locale)
                    logger.info("✅ Resent registration verification code to $email")

                    AppResult.Success(
                        SimpleResponse(
                            success = true,
                            message = locale.getString(StringResourcesKey.REGISTRATION_RESEND_SUCCESS_MESSAGE),
                            resendCodeTimeInSeconds = RegistrationPolicy.RESEND_COOLDOWN.inWholeSeconds
                        )
                    )
                }.getOrElse { error ->
                    logger.error("📧 Failed to resend registration email to $email", error)
                    locale.createError(
                        StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                        StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY
                    )
                }
            }
        }
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

        user ?: run {
            logger.error("🔥 signIn - User error - Not Expected")
            return locale.createError(
                StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY
            )
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
                            if (isKnownDevice && providedDeviceId != null) {
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
                    return AppResult.Success(
                        AuthResponse(
                            userId = user.userId,
                            deviceId = deviceDecision.deviceId,
                            authCode = AuthCode.SUCCESS_NEED_MFA
                        )
                    )
                }

                generateAuthenticatedResponse(
                    locale = locale,
                    userId = user.userId,
                    deviceId = deviceDecision.deviceId,
                    userRole = user.userRole,
                    jwtConfig = jwtConfig
                )
            }
        }
    }

    private sealed interface SignInPreCheck {
        data class Locked(val remainingMinutes: Long) : SignInPreCheck
        data class Ok(val user: UserSignInInfo?) : SignInPreCheck
    }

    private data class SignInDeviceDecision(
        val deviceId: UUID,
        val needsMfa: Boolean
    )

    private suspend fun handleFailedLoginAttemptTx(email: String, locale: Locale): AppResult.Failure {
        val result = runCatching {
            dbExecutor.tx {
                val attempt = loginAttemptRepository.findByEmail(email)
                val newAttemptCount = (attempt?.attempts ?: 0) + 1

                val now = System.currentTimeMillis()
                val newLockedUntil: Long? = when {
                    newAttemptCount >= LoginAttemptPolicy.MAX_ATTEMPTS_TIER_3 ->
                        now + LoginAttemptPolicy.LOCKOUT_DURATION_TIER_3_HOURS.hours.inWholeMilliseconds

                    newAttemptCount >= LoginAttemptPolicy.MAX_ATTEMPTS_TIER_2 ->
                        now + LoginAttemptPolicy.LOCKOUT_DURATION_TIER_2_HOURS.hours.inWholeMilliseconds

                    newAttemptCount >= LoginAttemptPolicy.MAX_ATTEMPTS_TIER_1 ->
                        now + LoginAttemptPolicy.LOCKOUT_DURATION_TIER_1_MINUTES.minutes.inWholeMilliseconds

                    else -> null
                }

                if (attempt == null) {
                    loginAttemptRepository.create(email)
                } else {
                    loginAttemptRepository.update(email, newAttemptCount, now, newLockedUntil)
                }

                newLockedUntil
            }
        }.getOrElse { error ->
            logger.error("🔥 signIn - Failed to update login attempts for email=$email", error)
            return locale.createError(
                StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY
            )
        }

        if (result != null) {
            val remainingMinutes = ((result - System.currentTimeMillis()) / 60000).coerceAtLeast(1)
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

        val userId = mfaCodeRequest.userId
        val deviceId = mfaCodeRequest.deviceId

        // 1) DB read (user) dentro de tx
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

        val (userId, deviceId, code) = mfaCodeVerificationRequest
        val hashedInput = hashingService.hashOpaqueToken(code)

        val userRole = runCatching {
            dbExecutor.tx {
                val latestCode = mfaCodeService.getLatestValidMfaCode(
                    userId,
                    deviceId,
                    MfaPurpose.SIGN_IN
                ) ?: return@tx null

                if (hashedInput != latestCode.hashedCode) {
                    return@tx null
                }

                val user = userRepository.getUserById(userId)
                    ?: return@tx null

                authRepository.completeMfaVerification(userId, deviceId, latestCode.id)

                user.userRole
            }
        }.getOrElse { error ->
            logger.error("🔥 verifyMfaCode DB error userId=$userId deviceId=$deviceId", error)
            return locale.createError(
                StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY
            )
        } ?: return locale.respondInvalidMfaCodeError()

        return generateAuthenticatedResponse(locale, userId, deviceId, userRole, jwtConfig)
    }

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

    private data class RefreshDbData(
        val validationInfo: RefreshTokenValidationInfo,
        val userRole: UserRole
    )


    suspend fun signOut(locale: Locale, deviceId: UUID): AppResult<SignOutResponse> {
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

    private sealed interface UpdatePasswordTxResult {
        data object InvalidToken : UpdatePasswordTxResult
        data object ExpiredToken : UpdatePasswordTxResult
        data object UserNotFound : UpdatePasswordTxResult
        data object UpdateFailed : UpdatePasswordTxResult
        data object Success : UpdatePasswordTxResult
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

    private suspend fun generateAuthenticatedResponse(
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

    private fun Locale.respondUserNotVerifiedError(): AppResult.Failure =
        createError(
            StringResourcesKey.AUTH_USER_NOT_VERIFIED_TITLE,
            StringResourcesKey.AUTH_USER_NOT_VERIFIED_DESCRIPTION,
            status = HttpStatusCode.Forbidden,
            errorCode = ErrorCode.AUTH_USER_NOT_VERIFIED
        )

    private fun Locale.respondSignOutError(): AppResult.Failure = createError(
        StringResourcesKey.AUTH_SIGN_OUT_FAILED_TITLE,
        StringResourcesKey.AUTH_SIGN_OUT_FAILED_DESCRIPTION,
        status = HttpStatusCode.InternalServerError,
        errorCode = ErrorCode.GENERAL_ERROR
    )

}


