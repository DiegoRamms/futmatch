package com.devapplab.service

import com.devapplab.utils.Constants
import com.devapplab.data.database.executor.DbExecutor
import com.devapplab.data.repository.user.UserRepository
import com.devapplab.model.AppResult
import com.devapplab.model.user.UserBaseInfo
import com.devapplab.model.user.mapper.toUserResponse
import com.devapplab.model.user.response.OrganizerListItem
import com.devapplab.model.user.response.UserResponse
import com.devapplab.model.payment.PaymentHistoryItem
import com.devapplab.service.image.ImageService
import com.devapplab.service.payment.PaymentServiceFactory
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.createError
import com.devapplab.utils.getString
import io.ktor.http.*
import io.ktor.http.content.*
import org.slf4j.LoggerFactory
import java.util.*

class UserService(
    private val dbExecutor: DbExecutor,
    private val userRepository: UserRepository,
    private val imageService: ImageService,
    private val paymentServiceFactory: PaymentServiceFactory
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun getUserById(userId: UUID?, locale: Locale): AppResult<UserResponse> {
        userId ?: return locale.createError(status = HttpStatusCode.NotFound)
        val userBaseInfo: UserBaseInfo = dbExecutor.tx { userRepository.getUserById(userId) }
            ?: return locale.createError(status = HttpStatusCode.NotFound)

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
        locale: Locale
    ): AppResult<String> {
        
        val currentUser = dbExecutor.tx { userRepository.getUserById(userId) }
            ?: return locale.createError(status = HttpStatusCode.NotFound)

        val path = "${Constants.BASE_USER_STORAGE_PATH}/$userId"
        
        // Save new image
        val savedImages = imageService.saveImages(multiPartData, path)
        if (savedImages.isEmpty()) {
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

        return AppResult.Success(
            data = locale.getString(StringResourcesKey.IMAGE_UPLOAD_SUCCESS_MESSAGE),
            appStatus = HttpStatusCode.OK
        )
    }

    suspend fun getOrganizers(): AppResult<List<OrganizerListItem>> {
        val organizers = dbExecutor.tx { userRepository.getOrganizers() }
        return AppResult.Success(data = organizers, appStatus = HttpStatusCode.OK)
    }

    suspend fun getPaymentHistory(userId: UUID, provider: com.devapplab.model.payment.PaymentProvider = com.devapplab.model.payment.PaymentProvider.STRIPE): AppResult<List<PaymentHistoryItem>> {
        logger.info("💳 [MATCH_TRACE] getPaymentHistory START | userId=$userId")

        val stripeCustomerId = userRepository.getPaymentProfile(userId, provider)
        if (stripeCustomerId.isNullOrBlank()) {
            logger.info("ℹ️ [MATCH_TRACE] getPaymentHistory | No Stripe customer found for user | userId=$userId")
            return AppResult.Success(emptyList())
        }

        val paymentService = paymentServiceFactory.getService(provider)
        val history = paymentService.getPaymentHistory(stripeCustomerId, daysBack = 30)

        logger.info("🏁 [MATCH_TRACE] getPaymentHistory END | userId=$userId | count=${history.size}")
        return AppResult.Success(data = history, appStatus = HttpStatusCode.OK)
    }
}
