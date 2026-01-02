package com.devapplab.service.auth

import com.devapplab.data.repository.AuthRepository
import com.devapplab.data.repository.RefreshTokenRepository
import com.devapplab.data.repository.UserRepository
import com.devapplab.data.repository.login_attempt.LoginAttemptRepository
import com.devapplab.data.repository.pending_registrations.PendingRegistrationRepository
import com.devapplab.model.AppResult
import com.devapplab.model.ErrorCode
import com.devapplab.model.auth.ClaimConfig
import com.devapplab.model.auth.JWTConfig
import com.devapplab.model.auth.RefreshTokenPayload
import com.devapplab.model.auth.UserSignInInfo
import com.devapplab.model.auth.request.CompleteRegistrationRequest
import com.devapplab.model.auth.request.ForgotPasswordRequest
import com.devapplab.model.auth.request.RegisterUserRequest
import com.devapplab.model.auth.request.ResendRegistrationCodeRequest
import com.devapplab.model.auth.request.SignInRequest
import com.devapplab.model.auth.response.*
import com.devapplab.model.mfa.response.MfaSendCodeResponse
import com.devapplab.model.password_reset.TokenVerificationResult
import com.devapplab.model.user.PendingUser
import com.devapplab.model.user.UserStatus
import com.devapplab.model.user.request.UpdatePasswordRequest
import com.devapplab.model.user.response.UpdatePasswordResponse
import com.devapplab.service.auth.auth_token.AuthTokenService
import com.devapplab.service.auth.mfa.MfaCodeService
import com.devapplab.service.auth.mfa.MfaRateLimitConfig
import com.devapplab.service.auth.refresh_token.RefreshTokenService
import com.devapplab.service.hashing.HashingService
import com.devapplab.service.password_reset.PasswordResetTokenService
import com.devapplab.utils.*
import io.ktor.http.*
import model.mfa.*
import model.user.User
import model.user.UserRole
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.slf4j.LoggerFactory
import service.auth.DeviceService
import service.email.EmailService
import utils.MfaUtils
import java.util.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


class AuthService(
    private val userRepository: UserRepository,
    private val hashingService: HashingService,
    private val authTokenService: AuthTokenService,
    private val refreshTokenService: RefreshTokenService,
    private val passwordResetTokenService: PasswordResetTokenService,
    private val deviceService: DeviceService,
    private val mfaCodeService: MfaCodeService,
    private val emailService: EmailService,
    private val authRepository: AuthRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val loginAttemptRepository: LoginAttemptRepository,
    private val pendingRegistrationRepository: PendingRegistrationRepository,
    private val mfaRateLimitConfig: MfaRateLimitConfig
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

    // DEPRECATED
    suspend fun addUser(
        user: User,
        locale: Locale,
        deviceInfo: String?
    ): AppResult<AuthResponse> {
        loginAttemptRepository.delete(user.email)

        if (deviceInfo.isNullOrBlank()) {
            return locale.respondDeviceInfoRequired()
        }
        val isEmailAlreadyRegistered = userRepository.isEmailAlreadyRegistered(user.email)
        if (isEmailAlreadyRegistered) return locale.respondIsEmailAlreadyRegisteredError()

        val isPhoneNumberAlreadyRegistered = userRepository.isPhoneNumberAlreadyRegistered(user.phone)
        if (isPhoneNumberAlreadyRegistered) return locale.respondIsPhoneAlreadyRegisteredError()

        val userWithPasswordHashed = user.copy(password = hashingService.hash(user.password))

        val authUserSavedData = authRepository.createUserWithDevice(userWithPasswordHashed, deviceInfo)

        val appResponse = AuthResponse(
            deviceId = authUserSavedData.deviceId,
            userId = authUserSavedData.userId,
            authCode = AuthCode.USER_CREATED
        )

        return AppResult.Success(appResponse)
    }

    suspend fun startRegistration(
        request: RegisterUserRequest,
        locale: Locale
    ): AppResult<SimpleResponse> {
        val email = request.email
        if (userRepository.isEmailAlreadyRegistered(email)) {
            logger.warn("⚠️ Registration attempt for already existing user: $email")
            return AppResult.Success(
                SimpleResponse(
                    success = true,
                    message = locale.getString(StringResourcesKey.REGISTRATION_EMAIL_SENT_MESSAGE)
                )
            )
        }

        try {
            pendingRegistrationRepository.findByEmail(email)?.let {
                pendingRegistrationRepository.delete(it.id)
            }

            val verificationCode = MfaUtils.generateCode()
            val hashedCode = hashingService.hashOpaqueToken(verificationCode)
            val expiresAt = System.currentTimeMillis() + RegistrationPolicy.EXPIRATION_DURATION.inWholeMilliseconds
            val hashedPassword = hashingService.hash(request.password)
            val requestWithHashedPassword = request.copy(password = hashedPassword)

            pendingRegistrationRepository.create(requestWithHashedPassword, hashedCode, expiresAt)
            
            emailService.sendRegistrationEmail(email, verificationCode)
            logger.info("✅ Sent registration verification code to $email")

        } catch (e: ExposedSQLException) {
            logger.error("🔥 ExposedSQLException during startRegistration, likely a race condition for email: $email. Error: ${e.message}")
        } catch (e: Exception) {
            logger.error("🔥 Unexpected error during startRegistration for email: $email. Error: ${e.message}")
        }
        
        return AppResult.Success(
            SimpleResponse(
                success = true,
                message = locale.getString(StringResourcesKey.REGISTRATION_EMAIL_SENT_MESSAGE)
            )
        )
    }

    suspend fun completeRegistration(
        request: CompleteRegistrationRequest,
        jwtConfig: JWTConfig,
        locale: Locale,
        deviceInfo: String? // Added deviceInfo
    ): AppResult<AuthResponse> {
        // Validate deviceInfo
        if (deviceInfo.isNullOrBlank()) {
            logger.error("❌ completeRegistration - Missing device info")
            return locale.respondDeviceInfoRequired()
        }

        val pendingRegistration = pendingRegistrationRepository.findByEmail(request.email)
            ?: return locale.createError(
                StringResourcesKey.REGISTRATION_CODE_INVALID_TITLE,
                StringResourcesKey.REGISTRATION_CODE_INVALID_DESCRIPTION
            )

        if (pendingRegistration.expiresAt < System.currentTimeMillis()) {
            pendingRegistrationRepository.delete(pendingRegistration.id)
            return locale.createError(
                StringResourcesKey.REGISTRATION_CODE_EXPIRED_TITLE,
                StringResourcesKey.REGISTRATION_CODE_EXPIRED_DESCRIPTION
            )
        }

        val isCodeValid =  hashingService.hashOpaqueToken(request.verificationCode) == pendingRegistration.verificationCode
        if (!isCodeValid) {
            return locale.createError(
                StringResourcesKey.REGISTRATION_CODE_INVALID_TITLE,
                StringResourcesKey.REGISTRATION_CODE_INVALID_DESCRIPTION
            )
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
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        // Pass PendingUser to userRepository.create
        val savedUser = userRepository.create(pendingUserToCreate)
            ?: return locale.createError(
                StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY
            )

        pendingRegistrationRepository.delete(pendingRegistration.id)
        loginAttemptRepository.delete(savedUser.email)

        val deviceId = savedUser.id?.let { authRepository.createDevice(it, deviceInfo, isTrusted = true) }  ?: return locale.createError(
            StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
            StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY
        )

        return generateAuthenticatedResponse(
            userId = savedUser.id,
            deviceId = deviceId,
            userRole = savedUser.role, // Use userRole from savedUser
            jwtConfig = jwtConfig
        )
    }

    suspend fun resendRegistrationCode(
        request: ResendRegistrationCodeRequest,
        locale: Locale
    ): AppResult<SimpleResponse> {
        val pendingRegistration = pendingRegistrationRepository.findByEmail(request.email)
            ?: return locale.createError(
                StringResourcesKey.REGISTRATION_NOT_FOUND_OR_EXPIRED_TITLE,
                StringResourcesKey.REGISTRATION_NOT_FOUND_OR_EXPIRED_DESCRIPTION
            )

        // Check for cooldown
        val timeSinceLastUpdate = System.currentTimeMillis() - pendingRegistration.updatedAt
        if (timeSinceLastUpdate < RegistrationPolicy.RESEND_COOLDOWN.inWholeMilliseconds) {
            val remainingSeconds = (RegistrationPolicy.RESEND_COOLDOWN.inWholeMilliseconds - timeSinceLastUpdate) / 1000
            return locale.createError(
                StringResourcesKey.REGISTRATION_RESEND_COOLDOWN_TITLE,
                StringResourcesKey.REGISTRATION_RESEND_COOLDOWN_DESCRIPTION,
                placeholders = mapOf("seconds" to remainingSeconds.toString())
            )
        }
        
        // Generate new code, update record, and send email
        val newVerificationCode = MfaUtils.generateCode()
        val newHashedCode = hashingService.hashOpaqueToken(newVerificationCode)
        val newExpiresAt = System.currentTimeMillis() + RegistrationPolicy.EXPIRATION_DURATION.inWholeMilliseconds
        val newUpdatedAt = System.currentTimeMillis()

        val updated = pendingRegistrationRepository.updateCodeAndExpiration(
            pendingRegistration.id,
            newHashedCode,
            newExpiresAt,
            newUpdatedAt
        )

        if (!updated) {
            logger.error("Failed to update pending registration code for email: ${request.email}")
            return locale.createError(
                StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY
            )
        }
        
        emailService.sendRegistrationEmail(request.email, newVerificationCode)
        logger.info("✅ Resent registration verification code to ${request.email}")

        return AppResult.Success(
            SimpleResponse(
                success = true,
                message = locale.getString(StringResourcesKey.REGISTRATION_RESEND_SUCCESS_MESSAGE)
            )
        )
    }


    suspend fun signIn(
        locale: Locale,
        signInRequest: SignInRequest,
        jwtConfig: JWTConfig,
        deviceInfo: String?,
    ): AppResult<AuthResponse> {
        val startTime = System.currentTimeMillis()

        val lockoutError = checkLockoutStatus(signInRequest.email, locale)
        if (lockoutError != null) {
            return lockoutError
        }

        if (deviceInfo.isNullOrBlank()) {
            logger.error("❌ signIn - Missing device info (Took ${System.currentTimeMillis() - startTime} ms)")
            return locale.respondDeviceInfoRequired()
        }

        val user = userRepository.getUserSignInInfo(signInRequest.email)
        if (user == null || !hashingService.verify(signInRequest.password, user.password)) {
            logger.error("❌ signIn - Invalid credentials for email: ${signInRequest.email} (Took ${System.currentTimeMillis() - startTime} ms)")
            return handleFailedLoginAttempt(signInRequest.email, locale)
        }

        handleSuccessfulLoginAttempt(signInRequest.email)

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
                logger.error("✅ signIn - Credentials verified, continuing to handleSuccessfulSignIn (Took ${System.currentTimeMillis() - startTime} ms)")
                handleSuccessfulSignIn(user, signInRequest, jwtConfig, deviceInfo)
            }
        }
    }

    private suspend fun checkLockoutStatus(email: String, locale: Locale): AppResult.Failure? {
        val attempt = loginAttemptRepository.findByEmail(email)
        if (attempt?.lockedUntil != null && attempt.lockedUntil > System.currentTimeMillis()) {
            val remainingLockoutMinutes = (attempt.lockedUntil - System.currentTimeMillis()) / 60000
            return locale.createError(
                StringResourcesKey.AUTH_ACCOUNT_LOCKED_TITLE,
                StringResourcesKey.AUTH_ACCOUNT_LOCKED_DESCRIPTION,
                status = HttpStatusCode.Forbidden,
                placeholders = mapOf("minutes" to remainingLockoutMinutes.toString())
            )
        }
        return null
    }

    private suspend fun handleFailedLoginAttempt(email: String, locale: Locale): AppResult.Failure {
        val attempt = loginAttemptRepository.findByEmail(email)
        val newAttemptCount = (attempt?.attempts ?: 0) + 1

        var newLockedUntil: Long? = null
        when {
            newAttemptCount >= LoginAttemptPolicy.MAX_ATTEMPTS_TIER_3 -> {
                newLockedUntil = System.currentTimeMillis() + LoginAttemptPolicy.LOCKOUT_DURATION_TIER_3_HOURS.hours.inWholeMilliseconds
            }
            newAttemptCount >= LoginAttemptPolicy.MAX_ATTEMPTS_TIER_2 -> {
                newLockedUntil = System.currentTimeMillis() + LoginAttemptPolicy.LOCKOUT_DURATION_TIER_2_HOURS.hours.inWholeMilliseconds
            }
            newAttemptCount >= LoginAttemptPolicy.MAX_ATTEMPTS_TIER_1 -> {
                newLockedUntil = System.currentTimeMillis() + LoginAttemptPolicy.LOCKOUT_DURATION_TIER_1_MINUTES.minutes.inWholeMilliseconds
            }
        }

        if (attempt == null) {
            loginAttemptRepository.create(email)
        } else {
            loginAttemptRepository.update(email, newAttemptCount, System.currentTimeMillis(), newLockedUntil)
        }

        if (newLockedUntil != null) {
            val remainingLockoutMinutes = (newLockedUntil - System.currentTimeMillis()) / 60000
            return locale.createError(
                StringResourcesKey.AUTH_ACCOUNT_LOCKED_TITLE,
                StringResourcesKey.AUTH_ACCOUNT_LOCKED_DESCRIPTION,
                status = HttpStatusCode.Forbidden,
                placeholders = mapOf("minutes" to remainingLockoutMinutes.toString())
            )
        }

        return locale.respondInvalidSignInCredentialsError()
    }

    private suspend fun handleSuccessfulLoginAttempt(email: String) {
        loginAttemptRepository.delete(email)
    }

    suspend fun sendMFACode(locale: Locale, mfaCodeRequest: MfaCodeRequest): AppResult<MfaSendCodeResponse> {
        val user = userRepository.getUserById(mfaCodeRequest.userId)
            ?: return locale.respondUserNotFoundError()

        if (user.status == UserStatus.BLOCKED) {
            return locale.respondSignInBlockedUserError()
        }

        val code = MfaUtils.generateCode()
        val expiresAt = MfaUtils.calculateExpiration(300)
        val hashedMfaCode = hashingService.hashOpaqueToken(code)

        val creationResult = mfaCodeService.createMfaCode(
            userId = user.id,
            deviceId = mfaCodeRequest.deviceId,
            hashedCode = hashedMfaCode,
            channel = MfaChannel.EMAIL,
            purpose = MfaPurpose.SIGN_IN,
            expiresAt = expiresAt,
            config = mfaRateLimitConfig
        )

        return when (creationResult) {
            is MfaCreationResult.Created -> {
                emailService.sendMfaCodeEmail(user.email, code)
                logger.info("✅ Sent MFA code to user ${user.id} for purpose SIGN_IN")
                AppResult.Success(
                    MfaSendCodeResponse(
                        newCodeSent = true,
                        expiresInSeconds = creationResult.expiresInSeconds
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

    suspend fun verifyMfaCode(
        locale: Locale,
        mfaCodeVerificationRequest: MfaCodeVerificationRequest,
        jwtConfig: JWTConfig
    ): AppResult<AuthResponse> {
        val (userId, deviceId, code) = mfaCodeVerificationRequest

        val latestCode = mfaCodeService.getLatestValidMfaCode(userId, deviceId, MfaPurpose.SIGN_IN)
            ?: return locale.respondInvalidMfaCodeError()

        val hashedInput = hashingService.hashOpaqueToken(code)

        val isValid = hashedInput == latestCode.hashedCode
        if (!isValid) {
            return locale.respondInvalidMfaCodeError()
        }

        val userRole = userRepository.getUserById(userId)?.userRole ?: UserRole.PLAYER

        authRepository.completeMfaVerification(userId, deviceId, latestCode.id)

        return generateAuthenticatedResponse(userId, deviceId, userRole, jwtConfig)
    }


    suspend fun refreshJwtToken(
        locale: Locale,
        currentRefreshToken: String?,
        refreshJWTRequest: RefreshJWTRequest,
        jwtConfig: JWTConfig
    ): AppResult<AuthResponse> {

        val (userId, deviceId) = refreshJWTRequest
        val refreshTokenValidationInfo = refreshTokenRepository.getValidationInfo(deviceId)
            ?: return locale.respondInvalidRefreshTokenError()

        val refreshTokenPayload = RefreshTokenPayload(
            plainToken = currentRefreshToken ?: return locale.respondInvalidRefreshTokenError(),
            hashedToken = refreshTokenValidationInfo.token,
            expiresAt = refreshTokenValidationInfo.expiresAt
        )

        val isRefreshTokenValid = refreshTokenService.isValidRefreshToken(refreshTokenPayload)
        if (!isRefreshTokenValid) return locale.respondInvalidRefreshTokenError()

        val userRole = userRepository.getUserById(userId)?.userRole ?: UserRole.PLAYER

        val claimConfig = ClaimConfig(userId, userRole)
        val accessToken = authTokenService.createAuthToken(claimConfig, jwtConfig)

        val expiresSoon =
            refreshTokenValidationInfo.expiresAt - System.currentTimeMillis() < jwtConfig.refreshTokenRotationThreshold.days.inWholeMilliseconds

        val (refreshToken, authCode) = if (expiresSoon) {
            val newPayload = refreshTokenService.generateRefreshToken(jwtConfig.refreshTokenLifetime)
            authRepository.rotateRefreshToken(userId, deviceId, newPayload)
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

    suspend fun signOut(locale: Locale, deviceId: UUID): AppResult<SignOutResponse> {
        val wasRevoke = authRepository.revokeRefreshToken(deviceId)
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
        val user = userRepository.findByEmail(forgotPasswordRequest.email)
            ?: return locale.respondUserNotFoundError()

        if (user.status == UserStatus.BLOCKED) {
            return locale.respondSignInBlockedUserError()
        }
        if (!user.isEmailVerified) {
            return locale.respondUserNotVerifiedError()
        }

        val code = MfaUtils.generateCode()
        val expiresAt = MfaUtils.calculateExpiration(300) // 5 minutes expiration
        val hashedMfaCode = hashingService.hashOpaqueToken(code)

        val creationResult = mfaCodeService.createMfaCode(
            userId = user.id,
            deviceId = null,
            hashedCode = hashedMfaCode,
            channel = MfaChannel.EMAIL,
            purpose = MfaPurpose.PASSWORD_RESET,
            expiresAt = expiresAt,
            config = mfaRateLimitConfig
        )

        return when (creationResult) {
            is MfaCreationResult.Created -> {
                emailService.sendMfaPasswordResetEmail(user.email, code)
                logger.info("✅ Sent password reset code to user ${user.id}")

                AppResult.Success(
                    ForgotPasswordResponse(
                        userId = user.id,
                        newCodeSent = true,
                        expiresInSeconds = creationResult.expiresInSeconds
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
        val userId = when (val tokenVerificationResult = passwordResetTokenService.verifyResetToken(resetToken)) {
            is TokenVerificationResult.Success -> tokenVerificationResult.userId
            is TokenVerificationResult.Invalid -> return locale.createError(
                StringResourcesKey.PASSWORD_RESET_TOKEN_INVALID_TITLE,
                StringResourcesKey.PASSWORD_RESET_TOKEN_INVALID_DESCRIPTION
            )

            is TokenVerificationResult.Expired -> return locale.createError(
                StringResourcesKey.PASSWORD_RESET_TOKEN_EXPIRED_TITLE,
                StringResourcesKey.PASSWORD_RESET_TOKEN_EXPIRED_DESCRIPTION
            )
        }

        val user = userRepository.getUserById(userId)
            ?: return locale.respondUserNotFoundError()

        val hashedPassword = hashingService.hash(request.newPassword)
        val passwordUpdated = userRepository.updatePassword(userId, hashedPassword)

        if (passwordUpdated) {
            passwordResetTokenService.invalidateToken(resetToken)
            handleSuccessfulLoginAttempt(user.email)
            return AppResult.Success(
                UpdatePasswordResponse(
                    success = true,
                    message = locale.getString(StringResourcesKey.PASSWORD_UPDATE_SUCCESS_MESSAGE)
                )
            )
        }

        return locale.createError(
            titleKey = StringResourcesKey.PASSWORD_UPDATE_FAILED_TITLE,
            descriptionKey = StringResourcesKey.PASSWORD_UPDATE_FAILED_DESCRIPTION,
            status = HttpStatusCode.InternalServerError
        )
    }

    suspend fun verifyResetMfa(
        locale: Locale,
        verifyResetMfaRequest: VerifyResetMfaRequest,
    ): AppResult<VerifyResetMfaResponse> {
        val userInfo =
            userRepository.getUserById(verifyResetMfaRequest.userId) ?: return locale.respondUserNotFoundError()

        val latestCode = mfaCodeService.getLatestValidMfaCode(userInfo.id, null, MfaPurpose.PASSWORD_RESET)
            ?: return locale.respondInvalidMfaCodeError()

        val hashedInput = hashingService.hashOpaqueToken(verifyResetMfaRequest.code)

        val isValid = hashedInput == latestCode.hashedCode
        if (!isValid) {
            return locale.respondInvalidMfaCodeError()
        }

        authRepository.completeForgotPasswordMfaVerification(latestCode.id)

        return generateResetPasswordTokenResponse(userInfo.id)
    }


    private suspend fun handleSuccessfulSignIn(
        user: UserSignInInfo,
        signInRequest: SignInRequest,
        jwtConfig: JWTConfig,
        deviceInfo: String
    ): AppResult<AuthResponse> {
        val startTime = System.currentTimeMillis()

        val providedDeviceId = signInRequest.deviceId
        logger.info("🔐 SignIn - Provided device ID: $providedDeviceId")
        val isKnownDevice = isKnownDeviceForUser(providedDeviceId, user.userId)
        val isDeviceTrusted =
            providedDeviceId?.let { deviceService.isTrustedDeviceIdForUser(providedDeviceId, user.userId) } ?: false
        logger.info("🔐 SignIn - Is known device: $isKnownDevice, Is trusted device: $isDeviceTrusted")
        val currentDeviceId = resolveDeviceId(providedDeviceId, isKnownDevice, deviceInfo, user.userId)
        logger.info("🔐 SignIn - Resolved device ID: $currentDeviceId")

        val needsMFA = !isKnownDevice || !isDeviceTrusted || !user.isEmailVerified
        logger.info("🔐 SignIn - Needs MFA: $needsMFA (Email Verified: ${user.isEmailVerified})")

        if (needsMFA) {
            val duration = System.currentTimeMillis() - startTime
            logger.info("🛡️ SignIn - MFA required, returning challenge (Took $duration ms)")
            return respondMFARequired(currentDeviceId, user.userId)
        }

        val duration = System.currentTimeMillis() - startTime
        logger.info("✅ SignIn - Authenticated without MFA (Took $duration ms)")
        return generateAuthenticatedResponse(user.userId, currentDeviceId, user.userRole, jwtConfig)
    }

    private suspend fun isKnownDeviceForUser(deviceId: UUID?, userId: UUID): Boolean {
        return deviceId?.let {
            deviceService.isValidDeviceIdForUser(it, userId)
        } ?: false
    }

    private suspend fun resolveDeviceId(
        providedDeviceId: UUID?,
        isKnownDevice: Boolean,
        deviceInfo: String,
        userId: UUID
    ): UUID {
        return when {
            isKnownDevice && providedDeviceId != null -> providedDeviceId
            else -> authRepository.createDevice(userId, deviceInfo)
        }
    }

    private fun respondMFARequired(deviceId: UUID, userId: UUID): AppResult<AuthResponse> {
        return AppResult.Success(
            AuthResponse(
                userId = userId,
                deviceId = deviceId,
                authCode = AuthCode.SUCCESS_NEED_MFA
            )
        )
    }

    private suspend fun generateAuthenticatedResponse(
        userId: UUID,
        deviceId: UUID,
        userRole: UserRole,
        jwtConfig: JWTConfig
    ): AppResult<AuthResponse> {
        val start = System.currentTimeMillis()
        val claimConfig = ClaimConfig(userId, userRole)
        val token = authTokenService.createAuthToken(claimConfig, jwtConfig)
        val refreshTokenPayload = refreshTokenService.generateRefreshToken(jwtConfig.refreshTokenLifetime)

        authRepository.rotateRefreshToken(userId, deviceId, refreshTokenPayload)

        val duration = System.currentTimeMillis() - start
        logger.info("✅ JWT + RefreshToken generated in $duration ms")
        return AppResult.Success(
            AuthResponse(
                authTokenResponse = AuthTokenResponse(
                    accessToken = token,
                    refreshToken = refreshTokenPayload.plainToken
                ),
                userId = userId,
                deviceId = deviceId,
                authCode = AuthCode.SUCCESS
            )
        )
    }

    private suspend fun generateResetPasswordTokenResponse(
        userId: UUID,
    ): AppResult<VerifyResetMfaResponse> {
        val resetToken = passwordResetTokenService.createAndSaveResetToken(userId)
        return AppResult.Success(VerifyResetMfaResponse(resetToken))
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
