package com.devapplab.service.auth.google

import com.devapplab.data.database.executor.DbExecutor
import com.devapplab.data.repository.auth.AuthIdentityRepository
import com.devapplab.data.repository.auth.AuthRepository
import com.devapplab.data.repository.user.UserRepository
import com.devapplab.model.AppResult
import com.devapplab.model.ErrorCode
import com.devapplab.model.auth.JWTConfig
import com.devapplab.model.auth.identity.AuthIdentity
import com.devapplab.model.auth.identity.AuthProvider
import com.devapplab.model.auth.request.GoogleAuthResolveRequest
import com.devapplab.model.auth.request.GoogleRegistrationRequest
import com.devapplab.model.auth.request.GoogleProfilePictureSource
import com.devapplab.model.auth.response.AuthResponse
import com.devapplab.model.user.User
import com.devapplab.model.user.UserRole
import com.devapplab.model.user.UserStatus
import com.devapplab.observability.AuthLogSeverity
import com.devapplab.observability.AuthRequestContext
import com.devapplab.observability.authEvent
import com.devapplab.service.auth.AuthenticatedResponseGenerator
import com.devapplab.service.image.ImageService
import com.devapplab.utils.Constants
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.createError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.Locale
import java.util.UUID

class GoogleRegistrationService(
    private val dbExecutor: DbExecutor,
    private val googleIdTokenVerifier: GoogleIdTokenVerifier,
    private val authIdentityRepository: AuthIdentityRepository,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val googleAuthService: GoogleAuthService,
    private val imageService: ImageService,
    private val authenticatedResponseGenerator: AuthenticatedResponseGenerator
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun register(
        request: GoogleRegistrationRequest,
        jwtConfig: JWTConfig,
        locale: Locale,
        deviceInfo: String?,
        context: AuthRequestContext
    ): AppResult<AuthResponse> {
        if (deviceInfo.isNullOrBlank()) {
            return locale.createError(StringResourcesKey.AUTH_DEVICE_INFO_REQUIRED_TITLE, StringResourcesKey.AUTH_DEVICE_INFO_REQUIRED_DESCRIPTION, status = HttpStatusCode.BadRequest)
        }
        val verified = withContext(Dispatchers.IO) { googleIdTokenVerifier.verify(request.idToken.trim()) }
        val identity = (verified as? GoogleIdTokenVerificationResult.Valid)?.identity ?: return locale.createError(
            StringResourcesKey.INVALID_JWT_TITLE, StringResourcesKey.INVALID_JWT_DESCRIPTION,
            status = HttpStatusCode.Unauthorized, errorCode = ErrorCode.AUTH_GOOGLE_TOKEN_INVALID
        )

        val created = runCatching {
            dbExecutor.tx {
                if (authIdentityRepository.findByProviderSubjectTx(AuthProvider.GOOGLE, identity.issuer, identity.subject) != null) {
                    return@tx null
                }
                if (userRepository.isEmailAlreadyRegistered(identity.email)) {
                    throw EmailAlreadyRegisteredException
                }
                val now = System.currentTimeMillis()
                val userId = userRepository.addUser(
                    User(
                        name = request.name.trim(), lastName = request.lastName.trim(), email = identity.email,
                        password = null, phone = request.phone.trim(), status = UserStatus.ACTIVE,
                        gender = request.gender, country = request.country.trim(), birthDate = request.birthDate,
                        playerPosition = request.playerPosition, profilePic = null, level = request.level,
                        role = UserRole.PLAYER, createdAt = now, updatedAt = now
                    )
                )
                authIdentityRepository.createTx(AuthIdentity(
                    userId = userId, provider = AuthProvider.GOOGLE, issuer = identity.issuer,
                    providerSubject = identity.subject, createdAt = now, lastAuthenticatedAt = now
                ))
                val deviceId = authRepository.createDevice(userId, deviceInfo, isTrusted = true)
                GoogleRegistrationCreated(userId, deviceId)
            }
        }.getOrElse { error ->
            if (error == EmailAlreadyRegisteredException) {
                return locale.createError(
                    StringResourcesKey.REGISTER_EMAIL_ALREADY_EXISTS_TITLE,
                    StringResourcesKey.REGISTER_EMAIL_ALREADY_EXISTS_DESCRIPTION,
                    status = HttpStatusCode.Conflict, errorCode = ErrorCode.ALREADY_EXISTS
                )
            }
            logger.authEvent(AuthLogSeverity.ERROR, "auth.google.register.failed", context, "failed", "db_error", throwable = error)
            return locale.createError(StringResourcesKey.GENERIC_TITLE_ERROR_KEY, StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY, status = HttpStatusCode.InternalServerError)
        }

        if (created == null) {
            val resolved = googleAuthService.resolve(
                GoogleAuthResolveRequest(request.idToken, request.deviceId), jwtConfig, locale, deviceInfo, context
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

        if (request.profilePictureSource == GoogleProfilePictureSource.GOOGLE) {
            identity.profilePictureUrl?.let { pictureUrl ->
            val imported = imageService.saveGoogleProfileImage(
                pictureUrl,
                "${Constants.BASE_USER_STORAGE_PATH}/${created.userId}"
            )
            val fileName = imported?.imageName?.substringAfterLast('/')
            if (fileName != null) {
                val updated = runCatching {
                    userRepository.updateProfilePic(created.userId, fileName)
                }.getOrDefault(false)
                if (!updated) {
                    imageService.deleteImages(imported.imageName)
                }
            }
            }
        }

        val response = authenticatedResponseGenerator.generate(locale, created.userId, created.deviceId, UserRole.PLAYER, jwtConfig)
        if (response is AppResult.Success) {
            logger.authEvent(AuthLogSeverity.INFO, "auth.google.register.success", context, "success", userId = created.userId, deviceId = created.deviceId)
        }
        return response
    }

    private data class GoogleRegistrationCreated(val userId: UUID, val deviceId: UUID)
    private data object EmailAlreadyRegisteredException : RuntimeException()
}
