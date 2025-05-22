package com.devapplab.service.auth

import com.devapplab.data.repository.AuthRepository
import com.devapplab.data.repository.RefreshTokenRepository
import com.devapplab.data.repository.UserRepository
import com.devapplab.model.AppResult
import com.devapplab.model.ErrorCode
import com.devapplab.model.auth.ClaimConfig
import com.devapplab.model.auth.JWTConfig
import com.devapplab.model.auth.RefreshTokenPayload
import com.devapplab.model.auth.UserSignInInfo
import com.devapplab.model.auth.request.SignInRequest
import com.devapplab.model.auth.response.AuthCode
import com.devapplab.model.auth.response.AuthResponse
import com.devapplab.model.auth.response.AuthTokenResponse
import com.devapplab.model.auth.response.RefreshJWTRequest
import com.devapplab.model.user.UserStatus
import com.devapplab.service.auth.auth_token.AuthTokenService
import com.devapplab.service.auth.mfa.MfaCodeService
import com.devapplab.service.auth.refresh_token.RefreshTokenService
import com.devapplab.service.hashing.HashingService
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.createError
import com.devapplab.utils.refreshTokenRotationThreshold
import io.ktor.http.*
import model.mfa.MfaChannel
import model.mfa.MfaCodeRequest
import model.mfa.MfaCodeVerificationRequest
import model.user.User
import model.user.UserRole
import service.auth.DeviceService
import service.email.EmailService
import utils.MfaUtils
import java.util.*

class AuthService(
    private val userRepository: UserRepository,
    private val hashingService: HashingService,
    private val authTokenService: AuthTokenService,
    private val refreshTokenService: RefreshTokenService,
    private val deviceService: DeviceService,
    private val mfaCodeService: MfaCodeService,
    private val emailService: EmailService,
    private val authRepository: AuthRepository,
    private val refreshTokenRepository: RefreshTokenRepository
) {
    suspend fun addUser(
        user: User,
        locale: Locale,
        deviceInfo: String?
    ): AppResult<AuthResponse> {
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

    suspend fun signIn(
        locale: Locale,
        signInRequest: SignInRequest,
        jwtConfig: JWTConfig,
        deviceInfo: String?,
    ): AppResult<AuthResponse> {
        val startTime = System.currentTimeMillis()

        if (deviceInfo.isNullOrBlank()) {
            println("❌ signIn - Missing device info (Took ${System.currentTimeMillis() - startTime} ms)")
            return locale.respondDeviceInfoRequired()
        }

        val user = userRepository.getUserSignInInfo(signInRequest.email)
            ?: run {
                println("❌ signIn - Invalid credentials (Took ${System.currentTimeMillis() - startTime} ms)")
                return locale.respondInvalidSignInCredentialsError()
            }

        if (!hashingService.verify(signInRequest.password, user.password)) {
            println("❌ signIn - Invalid credentials (Took ${System.currentTimeMillis() - startTime} ms)")
            return locale.respondInvalidSignInCredentialsError()
        }

        return when (user.status) {
            UserStatus.BLOCKED -> {
                println("❌ signIn - User is blocked (Took ${System.currentTimeMillis() - startTime} ms)")
                locale.respondSignInBlockedUserError()
            }
            UserStatus.SUSPENDED -> {
                println("❌ signIn - User is suspended (Took ${System.currentTimeMillis() - startTime} ms)")
                locale.respondSignInSuspendedUserError()
            }
            UserStatus.ACTIVE -> {
                println("✅ signIn - Credentials verified, continuing to handleSuccessfulSignIn (Took ${System.currentTimeMillis() - startTime} ms)")
                handleSuccessfulSignIn(user, signInRequest, jwtConfig, deviceInfo)
            }
        }
    }

    suspend fun sendMFACode(locale: Locale, mfaCodeRequest: MfaCodeRequest): AppResult<Boolean> {
        val userInfo = userRepository.getUserById(mfaCodeRequest.userId) ?: return locale.respondUserNotFoundError()
        val code = MfaUtils.generateCode()
        val expiresAt = MfaUtils.calculateExpiration(5)
        val hashedMfaCode = hashingService.hash(code)

        mfaCodeService.createMfaCode(
            mfaCodeRequest.userId, mfaCodeRequest.deviceId, hashedMfaCode, MfaChannel.EMAIL, expiresAt
        )

        emailService.sendMfaCodeEmail(userInfo.email, code)

        return AppResult.Success(true)
    }

    suspend fun verifyMfaCode(
        locale: Locale,
        mfaCodeVerificationRequest: MfaCodeVerificationRequest,
        jwtConfig: JWTConfig
    ): AppResult<AuthResponse> {
        val (userId, deviceId, code) = mfaCodeVerificationRequest

        val latestCode = mfaCodeService.getLatestValidMfaCode(userId, deviceId)
            ?: return locale.respondInvalidMfaCodeError()

        val isValid = hashingService.verify(code, latestCode.hashedCode)
        if (!isValid) return locale.respondInvalidMfaCodeError()

        val userRole = userRepository.getUserById(userId)?.userRole ?: UserRole.PLAYER

        authRepository.completeMfaVerification(userId, deviceId, latestCode.id)

        return generateAuthenticatedResponse(userId, deviceId,userRole,  jwtConfig)
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
            refreshTokenValidationInfo.expiresAt - System.currentTimeMillis() < refreshTokenRotationThreshold

        val (refreshToken, authCode) = if (expiresSoon) {
            val newPayload = refreshTokenService.generateRefreshToken()
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

    private suspend fun handleSuccessfulSignIn(
        user: UserSignInInfo,
        signInRequest: SignInRequest,
        jwtConfig: JWTConfig,
        deviceInfo: String
    ): AppResult<AuthResponse> {
        val startTime = System.currentTimeMillis()

        val providedDeviceId = signInRequest.deviceId
        println("🔐 SignIn - Provided device ID: $providedDeviceId")
        val isKnownDevice = isKnownDeviceForUser(providedDeviceId, user.userId)
        val isDeviceTrusted =
            providedDeviceId?.let { deviceService.isTrustedDeviceIdForUser(providedDeviceId, user.userId) } ?: false
        println("🔐 SignIn - Is known device: $isKnownDevice, Is trusted device: $isDeviceTrusted")
        val currentDeviceId = resolveDeviceId(providedDeviceId, isKnownDevice, deviceInfo, user.userId)
        println("🔐 SignIn - Resolved device ID: $currentDeviceId")

        val needsMFA = !isKnownDevice || !isDeviceTrusted || !user.isEmailVerified
        println("🔐 SignIn - Needs MFA: $needsMFA (Email Verified: ${user.isEmailVerified})")

        if (needsMFA) {
            val duration = System.currentTimeMillis() - startTime
            println("🛡️ SignIn - MFA required, returning challenge (Took $duration ms)")
            return respondMFARequired(currentDeviceId, user.userId)
        }

        val duration = System.currentTimeMillis() - startTime
        println("✅ SignIn - Authenticated without MFA (Took $duration ms)")
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
        val refreshTokenPayload = refreshTokenService.generateRefreshToken()

        authRepository.rotateRefreshToken(userId, deviceId, refreshTokenPayload)

        val duration = System.currentTimeMillis() - start
        println("✅ JWT + RefreshToken generated in $duration ms")
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
}