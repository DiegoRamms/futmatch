package com.devapplab.service.auth.apple

import com.devapplab.data.database.executor.DbExecutor
import com.devapplab.data.repository.auth.AuthIdentityRepository
import com.devapplab.data.repository.auth.AuthRepository
import com.devapplab.data.repository.device.DeviceRepository
import com.devapplab.data.repository.user.UserRepository
import com.devapplab.model.AppResult
import com.devapplab.model.ErrorCode
import com.devapplab.model.auth.JWTConfig
import com.devapplab.model.auth.identity.AuthProvider
import com.devapplab.model.auth.request.AppleAuthResolveRequest
import com.devapplab.model.auth.response.AppleAuthFlow
import com.devapplab.model.auth.response.AppleAuthResolveResponse
import com.devapplab.model.user.UserRole
import com.devapplab.model.user.UserStatus
import com.devapplab.observability.AuthLogSeverity
import com.devapplab.observability.AuthRequestContext
import com.devapplab.observability.authEvent
import com.devapplab.service.auth.AuthenticatedResponseGenerator
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.createError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.Locale
import java.util.UUID

class AppleAuthService(
    private val dbExecutor: DbExecutor,
    private val appleIdTokenVerifier: AppleIdTokenVerifier,
    private val authIdentityRepository: AuthIdentityRepository,
    private val userRepository: UserRepository,
    private val deviceRepository: DeviceRepository,
    private val authRepository: AuthRepository,
    private val authenticatedResponseGenerator: AuthenticatedResponseGenerator
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun resolve(
        request: AppleAuthResolveRequest,
        jwtConfig: JWTConfig,
        locale: Locale,
        deviceInfo: String?,
        context: AuthRequestContext
    ): AppResult<AppleAuthResolveResponse> {
        val startedAt = System.currentTimeMillis()
        val verification = withContext(Dispatchers.IO) {
            appleIdTokenVerifier.verify(request.identityToken.trim(), request.nonce.trim())
        }
        val verifiedIdentity = (verification as? AppleIdTokenVerificationResult.Valid)?.identity
            ?: run {
                val reason = (verification as AppleIdTokenVerificationResult.Invalid).reason
                logger.authEvent(
                    severity = AuthLogSeverity.WARN,
                    event = "auth.apple.resolve.failed",
                    context = context,
                    outcome = "rejected",
                    reason = reason,
                    statusCode = HttpStatusCode.Unauthorized.value,
                    durationMs = System.currentTimeMillis() - startedAt
                )
                return locale.createError(
                    StringResourcesKey.INVALID_JWT_TITLE,
                    StringResourcesKey.INVALID_JWT_DESCRIPTION,
                    status = HttpStatusCode.Unauthorized,
                    errorCode = ErrorCode.AUTH_APPLE_TOKEN_INVALID
                )
            }

        val lookup = runCatching {
            dbExecutor.tx {
                val authIdentity = authIdentityRepository.findByProviderSubjectTx(
                    provider = AuthProvider.APPLE,
                    issuer = verifiedIdentity.issuer,
                    providerSubject = verifiedIdentity.subject
                ) ?: return@tx AppleResolveLookup.SignUpRequired

                val user = userRepository.getUserSignInInfoById(authIdentity.userId)
                    ?: return@tx AppleResolveLookup.UserMissing

                if (user.status != UserStatus.ACTIVE) {
                    return@tx AppleResolveLookup.InactiveUser(user.status, user.userId)
                }

                if (deviceInfo.isNullOrBlank()) {
                    return@tx AppleResolveLookup.MissingDeviceInfo
                }

                val resolvedDeviceId = request.deviceId
                    ?.takeIf { deviceRepository.isValidDeviceIdForUser(it, user.userId) }
                    ?.also { deviceRepository.changeDeviceLastUsed(it) }
                    ?: authRepository.createDevice(user.userId, deviceInfo, isTrusted = true)

                authIdentityRepository.updateLastAuthenticatedAtTx(authIdentity.id!!, System.currentTimeMillis())
                AppleResolveLookup.Authenticated(
                    userId = user.userId,
                    userRole = user.userRole,
                    deviceId = resolvedDeviceId
                )
            }
        }.getOrElse { error ->
            logger.authEvent(
                severity = AuthLogSeverity.ERROR,
                event = "auth.apple.resolve.failed",
                context = context,
                outcome = "failed",
                reason = "db_error",
                throwable = error,
                durationMs = System.currentTimeMillis() - startedAt
            )
            return locale.createError(
                StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY,
                status = HttpStatusCode.InternalServerError,
                errorCode = ErrorCode.GENERAL_ERROR
            )
        }

        return when (lookup) {
            AppleResolveLookup.SignUpRequired -> {
                logger.authEvent(
                    severity = AuthLogSeverity.INFO,
                    event = "auth.apple.resolve.sign_up_required",
                    context = context,
                    outcome = "success",
                    durationMs = System.currentTimeMillis() - startedAt
                )
                AppResult.Success(AppleAuthResolveResponse(flow = AppleAuthFlow.SIGN_UP_REQUIRED))
            }

            AppleResolveLookup.UserMissing -> {
                logger.authEvent(
                    severity = AuthLogSeverity.ERROR,
                    event = "auth.apple.resolve.failed",
                    context = context,
                    outcome = "failed",
                    reason = "identity_user_missing",
                    durationMs = System.currentTimeMillis() - startedAt
                )
                locale.createError(
                    StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                    StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY,
                    status = HttpStatusCode.InternalServerError,
                    errorCode = ErrorCode.GENERAL_ERROR
                )
            }

            AppleResolveLookup.MissingDeviceInfo -> locale.createError(
                StringResourcesKey.AUTH_DEVICE_INFO_REQUIRED_TITLE,
                StringResourcesKey.AUTH_DEVICE_INFO_REQUIRED_DESCRIPTION,
                status = HttpStatusCode.BadRequest
            )

            is AppleResolveLookup.InactiveUser -> resolveInactiveUser(lookup, locale, context, startedAt)

            is AppleResolveLookup.Authenticated -> {
                val response = authenticatedResponseGenerator.generate(
                    locale = locale,
                    userId = lookup.userId,
                    deviceId = lookup.deviceId,
                    userRole = lookup.userRole,
                    jwtConfig = jwtConfig
                )
                if (response is AppResult.Success) {
                    logger.authEvent(
                        severity = AuthLogSeverity.INFO,
                        event = "auth.apple.resolve.success",
                        context = context,
                        outcome = "success",
                        userId = lookup.userId,
                        deviceId = lookup.deviceId,
                        durationMs = System.currentTimeMillis() - startedAt
                    )
                    AppResult.Success(
                        AppleAuthResolveResponse(
                            flow = AppleAuthFlow.AUTHENTICATED,
                            authResponse = response.data
                        )
                    )
                } else {
                    response as AppResult.Failure
                }
            }
        }
    }

    private fun resolveInactiveUser(
        lookup: AppleResolveLookup.InactiveUser,
        locale: Locale,
        context: AuthRequestContext,
        startedAt: Long
    ): AppResult.Failure {
        val error = when (lookup.status) {
            UserStatus.BLOCKED -> InactiveUserError(
                StringResourcesKey.AUTH_SIGN_IN_BLOCKED_TITLE,
                StringResourcesKey.AUTH_SIGN_IN_BLOCKED_DESCRIPTION,
                ErrorCode.AUTH_USER_BLOCKED,
                HttpStatusCode.Forbidden,
                "user_blocked"
            )

            UserStatus.SUSPENDED -> InactiveUserError(
                StringResourcesKey.AUTH_SIGN_IN_SUSPENDED_TITLE,
                StringResourcesKey.AUTH_SIGN_IN_SUSPENDED_DESCRIPTION,
                ErrorCode.AUTH_USER_SUSPENDED,
                HttpStatusCode.Forbidden,
                "user_suspended"
            )

            UserStatus.DELETED -> InactiveUserError(
                StringResourcesKey.AUTH_INVALID_SIGN_IN_TITLE,
                StringResourcesKey.AUTH_INVALID_SIGN_IN_DESCRIPTION,
                ErrorCode.AUTH_NEED_LOGIN,
                HttpStatusCode.Unauthorized,
                "deleted_user"
            )

            UserStatus.ACTIVE -> error("Active user cannot be resolved as inactive")
        }

        logger.authEvent(
            severity = AuthLogSeverity.WARN,
            event = "auth.apple.resolve.failed",
            context = context,
            outcome = "blocked",
            reason = error.reason,
            userId = lookup.userId,
            statusCode = error.statusCode.value,
            durationMs = System.currentTimeMillis() - startedAt
        )
        return locale.createError(error.title, error.description, status = error.statusCode, errorCode = error.errorCode)
    }

    private sealed interface AppleResolveLookup {
        data object SignUpRequired : AppleResolveLookup
        data object UserMissing : AppleResolveLookup
        data object MissingDeviceInfo : AppleResolveLookup
        data class InactiveUser(val status: UserStatus, val userId: UUID) : AppleResolveLookup
        data class Authenticated(val userId: UUID, val userRole: UserRole, val deviceId: UUID) : AppleResolveLookup
    }

    private data class InactiveUserError(
        val title: StringResourcesKey,
        val description: StringResourcesKey,
        val errorCode: ErrorCode,
        val statusCode: HttpStatusCode,
        val reason: String
    )
}
