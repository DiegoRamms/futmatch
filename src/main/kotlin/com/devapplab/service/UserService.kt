package com.devapplab.service

import com.devapplab.utils.Constants
import com.devapplab.data.database.executor.DbExecutor
import com.devapplab.data.repository.match.MatchRepository
import com.devapplab.data.repository.user.UserRepository
import com.devapplab.data.repository.RefreshTokenRepository
import com.devapplab.data.repository.device.DeviceRepository
import com.devapplab.data.repository.MfaCodeRepository
import com.devapplab.data.repository.mfa.LoginMfaChallengeRepository
import com.devapplab.data.repository.mfa.LoginMfaVerifyAttemptRepository
import com.devapplab.data.repository.password_reset.PasswordResetTokenRepository
import com.devapplab.data.repository.cleanup.ProfileImageCleanupRepository
import com.devapplab.model.AppResult
import com.devapplab.model.user.Gender
import com.devapplab.model.user.PlayerPosition
import com.devapplab.model.user.UserBaseInfo
import com.devapplab.model.user.UserStatus
import com.devapplab.model.user.request.DeleteAccountRequest
import com.devapplab.model.auth.RefreshTokenStatusReason
import com.devapplab.model.user.response.HomeLastMatchSection
import com.devapplab.model.user.response.HomeProfileSection
import com.devapplab.model.user.response.HomeSuggestedMatchSection
import com.devapplab.model.user.mapper.toUserResponse
import com.devapplab.model.user.response.UserHomeResponse
import com.devapplab.model.user.response.OrganizerListItem
import com.devapplab.model.user.response.UserResponse
import com.devapplab.model.payment.PaymentHistoryItem
import com.devapplab.observability.AppRequestContext
import com.devapplab.observability.appRejected
import com.devapplab.observability.appSuccess
import com.devapplab.service.image.ImageService
import com.devapplab.service.hashing.HashingService
import com.devapplab.service.match.MatchVisibilityRules
import com.devapplab.service.payment.PaymentServiceFactory
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.createError
import com.devapplab.utils.getString
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.util.*
import kotlin.math.roundToInt

class UserService(
    private val dbExecutor: DbExecutor,
    private val userRepository: UserRepository,
    private val matchRepository: MatchRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val deviceRepository: DeviceRepository,
    private val mfaCodeRepository: MfaCodeRepository,
    private val loginMfaChallengeRepository: LoginMfaChallengeRepository,
    private val loginMfaVerifyAttemptRepository: LoginMfaVerifyAttemptRepository,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val profileImageCleanupRepository: ProfileImageCleanupRepository,
    private val hashingService: HashingService,
    private val imageService: ImageService,
    private val paymentServiceFactory: PaymentServiceFactory
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun deleteAccount(userId: UUID, password: String, confirmation: String, locale: Locale, context: AppRequestContext): AppResult<String> {
        if (confirmation.trim().uppercase() != DeleteAccountRequest.REQUIRED_CONFIRMATION) {
            return locale.createError(
                StringResourcesKey.ACCOUNT_DELETION_CONFIRMATION_REQUIRED,
                StringResourcesKey.ACCOUNT_DELETION_CONFIRMATION_REQUIRED,
                status = HttpStatusCode.BadRequest
            )
        }
        val user = dbExecutor.tx { userRepository.getUserSignInInfoById(userId) }
            ?: return locale.createError(status = HttpStatusCode.NotFound)
        if (user.status != UserStatus.ACTIVE || !hashingService.verify(password.trim(), user.password)) {
            logger.appRejected(
                event = "user.account.deletion.rejected", context = context, reason = "invalid_password",
                userId = userId, statusCode = HttpStatusCode.Unauthorized.value
            )
            return locale.createError(
                StringResourcesKey.ACCOUNT_DELETION_INVALID_PASSWORD_TITLE,
                StringResourcesKey.ACCOUNT_DELETION_INVALID_PASSWORD_DESCRIPTION,
                status = HttpStatusCode.Unauthorized
            )
        }

        return performAccountDeletion(userId, locale, context)
    }

    suspend fun deleteAccountByAdministrator(targetUserId: UUID, locale: Locale, context: AppRequestContext): AppResult<String> =
        performAccountDeletion(targetUserId, locale, context)

    private suspend fun performAccountDeletion(userId: UUID, locale: Locale, context: AppRequestContext): AppResult<String> {
        val now = System.currentTimeMillis()
        val deletedPasswordHash = hashingService.hash(UUID.randomUUID().toString())
        val profilePic = dbExecutor.tx { userRepository.getUserById(userId)?.profilePic }
        val deletionResult = dbExecutor.tx {
            if (userRepository.hasAccountDeletionBlockersTx(userId)) return@tx null
            val updated = userRepository.anonymizeAccountTx(
                userId, "deleted+$userId@deleted.invalid", "deleted-$userId", deletedPasswordHash, now
            )
            if (!updated) return@tx null
            val cleanupJobId = profilePic?.takeIf(String::isNotBlank)?.let { fileName ->
                profileImageCleanupRepository.enqueueTx(
                    publicId = "${Constants.BASE_USER_STORAGE_PATH}/$userId/$fileName",
                    now = now
                )
            }
            refreshTokenRepository.revokeActiveTokensByUserId(userId, RefreshTokenStatusReason.ADMIN_REVOCATION, now)
            deviceRepository.deactivateDevicesByUserIdTx(userId, now)
            mfaCodeRepository.deleteByUserIdTx(userId)
            loginMfaChallengeRepository.revokeActiveByUserTx(userId, now)
            loginMfaVerifyAttemptRepository.deleteByUserIdTx(userId)
            passwordResetTokenRepository.deleteByUserId(userId)
            true to cleanupJobId
        } ?: return locale.createError(
            StringResourcesKey.ACCOUNT_DELETION_BLOCKED_TITLE,
            StringResourcesKey.ACCOUNT_DELETION_BLOCKED_DESCRIPTION,
            status = HttpStatusCode.Conflict
        )

        val cleanupJobId = deletionResult.second
        if (!profilePic.isNullOrBlank() && imageService.deleteImages("${Constants.BASE_USER_STORAGE_PATH}/$userId/$profilePic")) {
            cleanupJobId?.let { profileImageCleanupRepository.markCompleted(it, System.currentTimeMillis()) }
        }
        logger.appSuccess(
            event = "user.account.deletion.completed", context = context, userId = userId,
            statusCode = HttpStatusCode.OK.value
        )
        return AppResult.Success(locale.getString(StringResourcesKey.ACCOUNT_DELETION_SUCCESS_MESSAGE))
    }

    suspend fun getUserById(userId: UUID?, locale: Locale, context: AppRequestContext): AppResult<UserResponse> {
        userId ?: run {
            logger.appRejected(
                event = "user.profile.load_failed",
                context = context,
                reason = "missing_user_id",
                statusCode = HttpStatusCode.NotFound.value
            )
            return locale.createError(status = HttpStatusCode.NotFound)
        }
        val userBaseInfo: UserBaseInfo = dbExecutor.tx { userRepository.getUserById(userId) }
            ?: run {
                logger.appRejected(
                    event = "user.profile.load_failed",
                    context = context,
                    reason = "user_not_found",
                    userId = userId,
                    statusCode = HttpStatusCode.NotFound.value
                )
                return locale.createError(status = HttpStatusCode.NotFound)
            }

        // Generate signed URL for profile pic if exists
        val profilePicUrl = userBaseInfo.profilePic?.let { fileName ->
            val publicId = "${Constants.BASE_USER_STORAGE_PATH}/$userId/$fileName"
            imageService.getImageUrl(publicId)
        }.orEmpty()

        return AppResult.Success(userBaseInfo.toUserResponse(profilePicUrl))
    }

    suspend fun uploadProfilePic(
        userId: UUID,
        multiPartData: MultiPartData,
        locale: Locale,
        context: AppRequestContext
    ): AppResult<String> {
        val currentUser = dbExecutor.tx { userRepository.getUserById(userId) }
            ?: run {
                logger.appRejected(
                    event = "user.profile_picture.upload_failed",
                    context = context,
                    reason = "user_not_found",
                    userId = userId,
                    statusCode = HttpStatusCode.NotFound.value
                )
                return locale.createError(status = HttpStatusCode.NotFound)
            }

        val path = "${Constants.BASE_USER_STORAGE_PATH}/$userId"

        // Save new image
        val savedImages = imageService.saveImages(multiPartData, path)
        if (savedImages.isEmpty()) {
            logger.appRejected(
                event = "user.profile_picture.upload_failed",
                context = context,
                reason = "no_image_uploaded",
                userId = userId,
                statusCode = HttpStatusCode.BadRequest.value
            )
            return locale.createError(
                titleKey = StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                descriptionKey = StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY,
                status = HttpStatusCode.BadRequest
            )
        }
        
        val newImageName = savedImages.first().imageName.substringAfterLast('/')
        val updated = userRepository.updateProfilePic(userId, newImageName)

        if (!updated) {
            // Rollback: delete uploaded image if DB update fails
            val publicId = "${Constants.BASE_USER_STORAGE_PATH}/$userId/$newImageName"
            imageService.deleteImages(publicId)

            logger.appRejected(
                event = "user.profile_picture.upload_failed",
                context = context,
                reason = "profile_update_failed",
                userId = userId,
                statusCode = HttpStatusCode.InternalServerError.value
            )
            return locale.createError(
                titleKey = StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                descriptionKey = StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY,
                status = HttpStatusCode.InternalServerError
            )
        }

        // Delete old image if exists
        if (!currentUser.profilePic.isNullOrBlank()) {
            val oldPublicId = "${Constants.BASE_USER_STORAGE_PATH}/$userId/${currentUser.profilePic}"
            imageService.deleteImages(oldPublicId)
        }

        logger.appSuccess(
            event = "user.profile_picture.uploaded",
            context = context,
            userId = userId,
            statusCode = HttpStatusCode.OK.value
        )
        return AppResult.Success(
            data = locale.getString(StringResourcesKey.IMAGE_UPLOAD_SUCCESS_MESSAGE),
            appStatus = HttpStatusCode.OK
        )
    }

    suspend fun getOrganizers(): AppResult<List<OrganizerListItem>> {
        val organizers = dbExecutor.tx { userRepository.getOrganizers() }
        return AppResult.Success(data = organizers, appStatus = HttpStatusCode.OK)
    }

    suspend fun getPaymentHistory(
        userId: UUID,
        provider: com.devapplab.model.payment.PaymentProvider = com.devapplab.model.payment.PaymentProvider.STRIPE
    ): AppResult<List<PaymentHistoryItem>> {
        val stripeCustomerId = userRepository.getPaymentProfile(userId, provider)
        if (stripeCustomerId.isNullOrBlank()) {
            return AppResult.Success(emptyList())
        }

        val paymentService = paymentServiceFactory.getService(provider)
        val history = paymentService.getPaymentHistory(stripeCustomerId, daysBack = 30)

        return AppResult.Success(data = history, appStatus = HttpStatusCode.OK)
    }

    suspend fun updateName(userId: UUID, name: String, lastName: String, locale: Locale, context: AppRequestContext): AppResult<Unit> {
        val updated = dbExecutor.tx { userRepository.updateNameTx(userId, name.trim(), lastName.trim()) }
        return if (updated) {
            logger.appSuccess(
                event = "user.profile.name.updated",
                context = context,
                userId = userId,
                statusCode = HttpStatusCode.OK.value
            )
            AppResult.Success(data = Unit, appStatus = HttpStatusCode.OK)
        } else {
            logger.appRejected(
                event = "user.profile.name.update_failed",
                context = context,
                reason = "user_not_found",
                userId = userId,
                statusCode = HttpStatusCode.NotFound.value
            )
            locale.createError(status = HttpStatusCode.NotFound)
        }
    }

    suspend fun updateCountry(userId: UUID, countryCode: String, locale: Locale, context: AppRequestContext): AppResult<Unit> {
        val updated = dbExecutor.tx { userRepository.updateCountryTx(userId, countryCode.trim()) }
        return if (updated) {
            logger.appSuccess(
                event = "user.profile.country.updated",
                context = context,
                userId = userId,
                statusCode = HttpStatusCode.OK.value
            )
            AppResult.Success(data = Unit, appStatus = HttpStatusCode.OK)
        } else {
            logger.appRejected(
                event = "user.profile.country.update_failed",
                context = context,
                reason = "user_not_found",
                userId = userId,
                statusCode = HttpStatusCode.NotFound.value
            )
            locale.createError(status = HttpStatusCode.NotFound)
        }
    }

    suspend fun updateGender(userId: UUID, gender: String, locale: Locale, context: AppRequestContext): AppResult<Unit> {
        val genderEnum = try {
            Gender.valueOf(gender.uppercase())
        } catch (_: IllegalArgumentException) {
            logger.appRejected(
                event = "user.profile.gender.update_failed",
                context = context,
                reason = "invalid_gender",
                userId = userId,
                statusCode = HttpStatusCode.BadRequest.value
            )
            return locale.createError(
                titleKey = StringResourcesKey.USER_PROFILE_GENDER_INVALID,
                descriptionKey = StringResourcesKey.USER_PROFILE_GENDER_INVALID,
                status = HttpStatusCode.BadRequest
            )
        }

        val updated = dbExecutor.tx { userRepository.updateGenderTx(userId, genderEnum) }
        return if (updated) {
            logger.appSuccess(
                event = "user.profile.gender.updated",
                context = context,
                userId = userId,
                statusCode = HttpStatusCode.OK.value
            )
            AppResult.Success(data = Unit, appStatus = HttpStatusCode.OK)
        } else {
            logger.appRejected(
                event = "user.profile.gender.update_failed",
                context = context,
                reason = "user_not_found",
                userId = userId,
                statusCode = HttpStatusCode.NotFound.value
            )
            locale.createError(status = HttpStatusCode.NotFound)
        }
    }

    suspend fun updatePosition(userId: UUID, position: String, locale: Locale, context: AppRequestContext): AppResult<Unit> {
        val positionEnum = try {
            PlayerPosition.valueOf(position.uppercase())
        } catch (_: IllegalArgumentException) {
            logger.appRejected(
                event = "user.profile.position.update_failed",
                context = context,
                reason = "invalid_position",
                userId = userId,
                statusCode = HttpStatusCode.BadRequest.value
            )
            return locale.createError(
                titleKey = StringResourcesKey.USER_PROFILE_POSITION_INVALID,
                descriptionKey = StringResourcesKey.USER_PROFILE_POSITION_INVALID,
                status = HttpStatusCode.BadRequest
            )
        }

        val updated = dbExecutor.tx { userRepository.updatePositionTx(userId, positionEnum) }
        return if (updated) {
            logger.appSuccess(
                event = "user.profile.position.updated",
                context = context,
                userId = userId,
                statusCode = HttpStatusCode.OK.value
            )
            AppResult.Success(data = Unit, appStatus = HttpStatusCode.OK)
        } else {
            logger.appRejected(
                event = "user.profile.position.update_failed",
                context = context,
                reason = "user_not_found",
                userId = userId,
                statusCode = HttpStatusCode.NotFound.value
            )
            locale.createError(status = HttpStatusCode.NotFound)
        }
    }

    suspend fun getHome(userId: UUID?, locale: Locale, context: AppRequestContext): AppResult<UserHomeResponse> {
        userId ?: run {
            logger.appRejected(
                event = "user.home.load_failed",
                context = context,
                reason = "missing_user_id",
                statusCode = HttpStatusCode.NotFound.value
            )
            return locale.createError(status = HttpStatusCode.NotFound)
        }

        val user = dbExecutor.tx { userRepository.getUserById(userId) }
            ?: run {
                logger.appRejected(
                    event = "user.home.load_failed",
                    context = context,
                    reason = "user_not_found",
                    userId = userId,
                    statusCode = HttpStatusCode.NotFound.value
                )
                return locale.createError(status = HttpStatusCode.NotFound)
            }

        val (suggestedMatches, lastMatch, winStats) = coroutineScope {
            val suggestedDeferred = async {
                matchRepository.getHomeSuggestedMatches(userId = userId, limit = HOME_SUGGESTED_MATCHES_FETCH_LIMIT)
            }
            val lastMatchDeferred = async { matchRepository.getHomeLastMatch(userId) }
            val winStatsDeferred = async { matchRepository.getHomeWinStats(userId) }
            Triple(
                suggestedDeferred.await(),
                lastMatchDeferred.await(),
                winStatsDeferred.await()
            )
        }

        val averageScore = if (winStats.playedMatches == 0) {
            0
        } else {
            ((winStats.wonMatches.toDouble() / winStats.playedMatches.toDouble()) * 100.0).roundToInt()
        }

        val profileImageUrl = user.profilePic?.let { fileName ->
            imageService.getImageUrl("${Constants.BASE_USER_STORAGE_PATH}/${user.id}/$fileName")
        }

        val suggestedResponse = suggestedMatches
            .filter { item ->
                MatchVisibilityRules.isVisibleFor(
                    userGender = user.gender,
                    userLevel = user.level,
                    matchGenderType = item.genderType,
                    matchLevel = item.playerLevel
                )
            }
            .take(HOME_SUGGESTED_MATCHES_LIMIT)
            .map { item ->
            val imageUrl = item.fieldImageKey?.let { imageKey ->
                imageService.getImageUrl("${Constants.BASE_FIELD_STORAGE_PATH}/${item.fieldId}/$imageKey")
            }
            HomeSuggestedMatchSection(
                matchId = item.matchId,
                fieldId = item.fieldId,
                fieldName = item.fieldName,
                startTime = item.startTime,
                endTime = item.endTime,
                priceInCents = item.price.multiply(BigDecimal(100)).longValueExact(),
                imageUrl = imageUrl
            )
        }

        val lastMatchResponse = lastMatch?.let {
            HomeLastMatchSection(
                matchId = it.matchId,
                fieldId = it.fieldId,
                fieldName = it.fieldName,
                playedAt = it.playedAt,
                outcome = it.outcome,
                teamAScore = it.teamAScore,
                teamBScore = it.teamBScore
            )
        }

        return AppResult.Success(
            data = UserHomeResponse(
                profile = HomeProfileSection(
                    greetingName = user.name,
                    level = user.level,
                    averageScore = averageScore,
                    profileImageUrl = profileImageUrl
                ),
                suggestedMatches = suggestedResponse,
                lastMatch = lastMatchResponse
            ),
            appStatus = HttpStatusCode.OK
        )
    }

    private companion object {
        const val HOME_SUGGESTED_MATCHES_LIMIT = 4
        const val HOME_SUGGESTED_MATCHES_FETCH_LIMIT = 12
    }
}
