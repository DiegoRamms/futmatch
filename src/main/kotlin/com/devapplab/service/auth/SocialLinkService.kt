package com.devapplab.service.auth

import com.devapplab.data.database.executor.DbExecutor
import com.devapplab.data.repository.MfaCodeRepository
import com.devapplab.data.repository.auth.AuthIdentityRepository
import com.devapplab.data.repository.auth.AuthRepository
import com.devapplab.data.repository.auth.SocialLinkAttemptRepository
import com.devapplab.data.repository.auth.AppleAuthTokenRepository
import com.devapplab.data.repository.device.DeviceRepository
import com.devapplab.data.repository.user.UserRepository
import com.devapplab.model.AppResult
import com.devapplab.model.auth.JWTConfig
import com.devapplab.model.auth.identity.AuthIdentity
import com.devapplab.model.auth.identity.AuthProvider
import com.devapplab.model.auth.request.*
import com.devapplab.model.auth.response.AuthResponse
import com.devapplab.model.auth.response.SocialLinkStartResponse
import com.devapplab.model.mfa.MfaChannel
import com.devapplab.model.mfa.MfaCreationResult
import com.devapplab.model.mfa.MfaPurpose
import com.devapplab.model.user.UserStatus
import com.devapplab.service.auth.apple.AppleIdTokenVerificationResult
import com.devapplab.service.auth.apple.AppleIdTokenVerifier
import com.devapplab.service.auth.apple.AppleTokenExchangeService
import com.devapplab.service.pii.PiiCrypto
import com.devapplab.service.auth.google.GoogleIdTokenVerificationResult
import com.devapplab.service.auth.google.GoogleIdTokenVerifier
import com.devapplab.service.auth.mfa.MfaCodeService
import com.devapplab.service.auth.mfa.MfaRateLimitConfig
import com.devapplab.service.email.EmailService
import com.devapplab.service.hashing.HashingService
import com.devapplab.utils.MfaUtils
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.createError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class SocialLinkService(
    private val dbExecutor: DbExecutor, private val attempts: SocialLinkAttemptRepository, private val attemptTokens: SocialLinkAttemptTokenService,
    private val users: UserRepository, private val hashing: HashingService, private val mfa: MfaCodeService, private val mfaRepo: MfaCodeRepository,
    private val email: EmailService, private val mfaConfig: MfaRateLimitConfig, private val identities: AuthIdentityRepository,
    private val authRepository: AuthRepository, private val devices: DeviceRepository, private val google: GoogleIdTokenVerifier,
    private val apple: AppleIdTokenVerifier, private val appleTokenExchange: AppleTokenExchangeService, private val appleTokens: AppleAuthTokenRepository,
    private val piiCrypto: PiiCrypto, private val authenticated: AuthenticatedResponseGenerator
) {
    suspend fun start(request: StartSocialLinkRequest, locale: Locale): AppResult<SocialLinkStartResponse> {
        val precheck = dbExecutor.tx {
            val attempt = attempts.findValidByTokenHashTx(attemptTokens.hash(request.linkAttemptToken.trim()), System.currentTimeMillis()) ?: return@tx null
            val user = users.getUserSignInInfoById(attempt.userId) ?: return@tx null
            attempt to user
        } ?: return locale.createError(StringResourcesKey.AUTH_INVALID_SIGN_IN_TITLE, StringResourcesKey.AUTH_INVALID_SIGN_IN_DESCRIPTION, status = HttpStatusCode.Unauthorized)
        val password = precheck.second.password
        if (precheck.second.status != UserStatus.ACTIVE || !precheck.second.isEmailVerified || password == null || !hashing.verify(request.password, password)) {
            return locale.createError(StringResourcesKey.AUTH_INVALID_SIGN_IN_TITLE, StringResourcesKey.AUTH_INVALID_SIGN_IN_DESCRIPTION, status = HttpStatusCode.Unauthorized)
        }
        val result = dbExecutor.tx {
            val attempt = attempts.findValidByTokenHashTx(attemptTokens.hash(request.linkAttemptToken.trim()), System.currentTimeMillis()) ?: return@tx null
            if (attempt.id != precheck.first.id) return@tx null
            val user = users.getUserSignInInfoById(attempt.userId) ?: return@tx null
            val code = MfaUtils.generateCode(); val expires = MfaUtils.calculateExpiration(300)
            when (val created = mfa.createMfaCode(user.userId, null, hashing.hashOpaqueToken(code), MfaChannel.EMAIL, MfaPurpose.LINK_SOCIAL, expires, mfaConfig)) {
                is MfaCreationResult.Created -> { attempts.setMfaCodeTx(attempt.id, created.codeId, System.currentTimeMillis()); Triple(users.getUserById(user.userId)!!.email, code, created.expiresInSeconds) }
                else -> return@tx null
            }
        } ?: return locale.createError(StringResourcesKey.AUTH_INVALID_SIGN_IN_TITLE, StringResourcesKey.AUTH_INVALID_SIGN_IN_DESCRIPTION, status = HttpStatusCode.Unauthorized)
        email.sendMfaCodeEmail(result.first, result.second, locale)
        return AppResult.Success(SocialLinkStartResponse(true, result.third, mfaConfig.minWaitSeconds))
    }

    suspend fun confirmGoogle(request: ConfirmGoogleSocialLinkRequest, jwt: JWTConfig, locale: Locale, deviceInfo: String?): AppResult<AuthResponse> {
        val identity = withContext(Dispatchers.IO) { google.verify(request.idToken.trim()) }.let { (it as? GoogleIdTokenVerificationResult.Valid)?.identity }
            ?: return locale.createError(StringResourcesKey.INVALID_JWT_TITLE, StringResourcesKey.INVALID_JWT_DESCRIPTION, status = HttpStatusCode.Unauthorized)
        return confirm(request.linkAttemptToken, request.code, AuthProvider.GOOGLE, identity.issuer, identity.subject, request.deviceId, jwt, locale, deviceInfo)
    }
    suspend fun confirmApple(request: ConfirmAppleSocialLinkRequest, jwt: JWTConfig, locale: Locale, deviceInfo: String?): AppResult<AuthResponse> {
        val identity = withContext(Dispatchers.IO) { apple.verify(request.identityToken.trim(), request.nonce.trim()) }.let { (it as? AppleIdTokenVerificationResult.Valid)?.identity }
            ?: return locale.createError(StringResourcesKey.INVALID_JWT_TITLE, StringResourcesKey.INVALID_JWT_DESCRIPTION, status = HttpStatusCode.Unauthorized)
        val result = confirm(request.linkAttemptToken, request.code, AuthProvider.APPLE, identity.issuer, identity.subject, request.deviceId, jwt, locale, deviceInfo)
        if (result is AppResult.Success && result.data.userId != null) {
            runCatching { appleTokenExchange.exchangeAuthorizationCode(request.authorizationCode.trim()) }
                .getOrNull()?.let { refresh -> appleTokens.upsertTx(result.data.userId, piiCrypto.encrypt(refresh), piiCrypto.keyVersion) }
        }
        return result
    }
    private suspend fun confirm(token: String, code: String, provider: AuthProvider, issuer: String, subject: String, deviceId: java.util.UUID?, jwt: JWTConfig, locale: Locale, deviceInfo: String?): AppResult<AuthResponse> {
        if (deviceInfo.isNullOrBlank()) return locale.createError(StringResourcesKey.AUTH_DEVICE_INFO_REQUIRED_TITLE, StringResourcesKey.AUTH_DEVICE_INFO_REQUIRED_DESCRIPTION, status = HttpStatusCode.BadRequest)
        val result = dbExecutor.tx {
            val a = attempts.findValidByTokenHashTx(attemptTokens.hash(token.trim()), System.currentTimeMillis()) ?: return@tx null
            if (a.provider != provider || a.issuer != issuer || a.providerSubject != subject || a.passwordVerifiedAt == null || a.mfaCodeId == null) return@tx null
            val mfaData = mfaRepo.findByIdTx(a.mfaCodeId) ?: return@tx null
            if (mfaData.userId != a.userId || mfaData.purpose != MfaPurpose.LINK_SOCIAL || mfaData.verified || mfaData.expiresAt < System.currentTimeMillis() || hashing.hashOpaqueToken(code) != mfaData.hashedCode) return@tx null
            if (identities.findByProviderSubjectTx(provider, issuer, subject) != null) return@tx null
            identities.createTx(AuthIdentity(userId = a.userId, provider = provider, issuer = issuer, providerSubject = subject, createdAt = System.currentTimeMillis(), lastAuthenticatedAt = System.currentTimeMillis()))
            mfaRepo.markAsVerified(a.mfaCodeId); attempts.consumeTx(a.id, System.currentTimeMillis())
            val u = users.getUserSignInInfoById(a.userId) ?: return@tx null
            val resolvedDevice = deviceId?.takeIf { devices.isValidDeviceIdForUser(it, a.userId) } ?: authRepository.createDevice(a.userId, deviceInfo, true)
            u to resolvedDevice
        } ?: return locale.createError(StringResourcesKey.AUTH_INVALID_SIGN_IN_TITLE, StringResourcesKey.AUTH_INVALID_SIGN_IN_DESCRIPTION, status = HttpStatusCode.Unauthorized)
        return authenticated.generate(locale, result.first.userId, result.second, result.first.userRole, jwt)
    }
}
