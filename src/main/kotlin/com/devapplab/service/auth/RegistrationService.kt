package com.devapplab.service.auth

import com.devapplab.data.database.executor.DbExecutor
import com.devapplab.data.repository.auth.AuthRepository
import com.devapplab.data.repository.login_attempt.LoginAttemptRepository
import com.devapplab.data.repository.pending_registrations.PendingRegistrationRepository
import com.devapplab.data.repository.user.UserRepository
import com.devapplab.model.AppResult
import com.devapplab.model.auth.JWTConfig
import com.devapplab.model.auth.request.CompleteRegistrationRequest
import com.devapplab.model.auth.request.RegisterUserRequest
import com.devapplab.model.auth.request.ResendRegistrationCodeRequest
import com.devapplab.model.auth.response.AuthResponse
import com.devapplab.model.auth.response.SimpleResponse
import com.devapplab.model.user.PendingUser
import com.devapplab.model.user.UserStatus
import com.devapplab.service.auth.state.CompleteRegistrationAbort
import com.devapplab.service.auth.state.CompleteRegistrationFailure
import com.devapplab.service.auth.state.RegistrationTxResult
import com.devapplab.service.auth.state.ResendRegistrationCodeDecision
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
import kotlin.time.Duration.Companion.seconds


class RegistrationService(
    private val dbExecutor: DbExecutor,
    private val userRepository: UserRepository,
    private val hashingService: HashingService,
    private val pendingRegistrationRepository: PendingRegistrationRepository,
    private val emailService: EmailService,
    private val loginAttemptRepository: LoginAttemptRepository,
    private val authRepository: AuthRepository,
    private val authenticatedResponseGenerator: AuthenticatedResponseGenerator
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

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
            return locale.createError(
                StringResourcesKey.AUTH_DEVICE_INFO_REQUIRED_TITLE,
                StringResourcesKey.AUTH_DEVICE_INFO_REQUIRED_DESCRIPTION,
                status = HttpStatusCode.BadRequest
            )
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

                RegistrationTxResult(savedUser, deviceId, userId)
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

        return authenticatedResponseGenerator.generate(
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
}
