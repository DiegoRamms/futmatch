package com.devapplab.service.auth.apple

import com.devapplab.data.database.executor.DbExecutor
import com.devapplab.data.repository.auth.AppleAuthTokenRepository
import com.devapplab.data.repository.auth.AuthIdentityRepository
import com.devapplab.data.repository.auth.AuthRepository
import com.devapplab.data.repository.user.UserRepository
import com.devapplab.model.AppResult
import com.devapplab.model.ErrorCode
import com.devapplab.model.auth.JWTConfig
import com.devapplab.model.auth.identity.AuthIdentity
import com.devapplab.model.auth.identity.AuthProvider
import com.devapplab.model.auth.request.AppleAuthResolveRequest
import com.devapplab.model.auth.request.AppleRegistrationRequest
import com.devapplab.model.auth.response.AuthResponse
import com.devapplab.model.user.User
import com.devapplab.model.user.UserRole
import com.devapplab.model.user.UserStatus
import com.devapplab.observability.AuthLogSeverity
import com.devapplab.observability.AuthRequestContext
import com.devapplab.observability.authEvent
import com.devapplab.service.auth.AuthenticatedResponseGenerator
import com.devapplab.service.pii.PiiCrypto
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.createError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.Locale
import java.util.UUID

class AppleRegistrationService(
    private val dbExecutor: DbExecutor,
    private val appleIdTokenVerifier: AppleIdTokenVerifier,
    private val authIdentityRepository: AuthIdentityRepository,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val appleAuthService: AppleAuthService,
    private val appleTokenExchangeService: AppleTokenExchangeService,
    private val appleAuthTokenRepository: AppleAuthTokenRepository,
    private val piiCrypto: PiiCrypto,
    private val authenticatedResponseGenerator: AuthenticatedResponseGenerator
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun register(
        request: AppleRegistrationRequest,
        jwtConfig: JWTConfig,
        locale: Locale,
        deviceInfo: String?,
        context: AuthRequestContext
    ): AppResult<AuthResponse> {
        if (deviceInfo.isNullOrBlank()) {
            return locale.createError(StringResourcesKey.AUTH_DEVICE_INFO_REQUIRED_TITLE, StringResourcesKey.AUTH_DEVICE_INFO_REQUIRED_DESCRIPTION, status = HttpStatusCode.BadRequest)
        }
        val verified = withContext(Dispatchers.IO) {
            appleIdTokenVerifier.verify(request.identityToken.trim(), request.nonce.trim())
        }
        val identity = (verified as? AppleIdTokenVerificationResult.Valid)?.identity ?: return locale.createError(
            StringResourcesKey.INVALID_JWT_TITLE, StringResourcesKey.INVALID_JWT_DESCRIPTION,
            status = HttpStatusCode.Unauthorized, errorCode = ErrorCode.AUTH_APPLE_TOKEN_INVALID
        )

        // Unlike Google, register is the only place Apple ever gives us an email, and
        // only on a first-ever authorization. If it's missing there is no account to
        // create — the user has to revoke the app in Settings and re-authorize fresh.
        val email = identity.email ?: return locale.createError(
            StringResourcesKey.AUTH_APPLE_EMAIL_UNAVAILABLE_TITLE,
            StringResourcesKey.AUTH_APPLE_EMAIL_UNAVAILABLE_DESCRIPTION,
            status = HttpStatusCode.BadRequest,
            errorCode = ErrorCode.AUTH_APPLE_EMAIL_UNAVAILABLE
        )

        val created = runCatching {
            dbExecutor.tx {
                if (authIdentityRepository.findByProviderSubjectTx(AuthProvider.APPLE, identity.issuer, identity.subject) != null) {
                    return@tx null
                }
                if (userRepository.isEmailAlreadyRegistered(email)) {
                    throw EmailAlreadyRegisteredException
                }
                val now = System.currentTimeMillis()
                val userId = userRepository.addUser(
                    User(
                        name = request.name.trim(), lastName = request.lastName.trim(), email = email,
                        // No avatar branch: Apple never returns a profile picture.
                        password = null, phone = request.phone.trim(), status = UserStatus.ACTIVE,
                        gender = request.gender, country = request.country.trim(), birthDate = request.birthDate,
                        playerPosition = request.playerPosition, profilePic = null, level = request.level,
                        role = UserRole.PLAYER, createdAt = now, updatedAt = now
                    )
                )
                authIdentityRepository.createTx(AuthIdentity(
                    userId = userId, provider = AuthProvider.APPLE, issuer = identity.issuer,
                    providerSubject = identity.subject, createdAt = now, lastAuthenticatedAt = now
                ))
                val deviceId = authRepository.createDevice(userId, deviceInfo, isTrusted = true)
                AppleRegistrationCreated(userId, deviceId)
            }
        }.getOrElse { error ->
            if (error == EmailAlreadyRegisteredException) {
                return locale.createError(
                    StringResourcesKey.REGISTER_EMAIL_ALREADY_EXISTS_TITLE,
                    StringResourcesKey.REGISTER_EMAIL_ALREADY_EXISTS_DESCRIPTION,
                    status = HttpStatusCode.Conflict, errorCode = ErrorCode.ALREADY_EXISTS
                )
            }
            logger.authEvent(AuthLogSeverity.ERROR, "auth.apple.register.failed", context, "failed", "db_error", throwable = error)
            return locale.createError(StringResourcesKey.GENERIC_TITLE_ERROR_KEY, StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY, status = HttpStatusCode.InternalServerError)
        }

        if (created == null) {
            val resolved = appleAuthService.resolve(
                AppleAuthResolveRequest(request.identityToken, request.nonce, request.deviceId), jwtConfig, locale, deviceInfo, context
            )
            if (resolved is AppResult.Success) {
                val authResponse = resolved.data.authResponse
                if (authResponse != null) {
                    return AppResult.Success(authResponse)
                }
                return locale.createError(
                    StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                    StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY,
                    status = HttpStatusCode.Conflict
                )
            }
            return resolved as AppResult.Failure
        }

        // Best-effort: a failed exchange must not fail the registration. It only means
        // this account cannot be revoked server-side later, which is a degradation, not
        // a lost sign-up.
        runCatching {
            val refreshToken = appleTokenExchangeService.exchangeAuthorizationCode(request.authorizationCode.trim())
            if (refreshToken != null) {
                appleAuthTokenRepository.upsertTx(
                    userId = created.userId,
                    refreshTokenCiphertext = piiCrypto.encrypt(refreshToken),
                    piiKeyVersion = piiCrypto.keyVersion
                )
            } else {
                logger.authEvent(AuthLogSeverity.WARN, "auth.apple.register.token_exchange_missing", context, "degraded", userId = created.userId)
            }
        }.onFailure { error ->
            logger.authEvent(AuthLogSeverity.WARN, "auth.apple.register.token_exchange_failed", context, "degraded", throwable = error, userId = created.userId)
        }

        val response = authenticatedResponseGenerator.generate(locale, created.userId, created.deviceId, UserRole.PLAYER, jwtConfig)
        if (response is AppResult.Success) {
            logger.authEvent(AuthLogSeverity.INFO, "auth.apple.register.success", context, "success", userId = created.userId, deviceId = created.deviceId)
        }
        return response
    }

    private data class AppleRegistrationCreated(val userId: UUID, val deviceId: UUID)
    private data object EmailAlreadyRegisteredException : RuntimeException()
}
