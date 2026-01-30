package com.devapplab.service

import com.devapplab.data.database.executor.DbExecutor
import com.devapplab.data.repository.user.UserRepository
import com.devapplab.model.AppResult
import com.devapplab.model.user.UserBaseInfo
import com.devapplab.model.user.mapper.toUserResponse
import com.devapplab.model.user.response.UserResponse
import com.devapplab.service.image.ImageService
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.createError
import com.devapplab.utils.getString
import io.ktor.http.*
import io.ktor.http.content.*
import java.util.*

class UserService(
    private val dbExecutor: DbExecutor,
    private val userRepository: UserRepository,
    private val imageService: ImageService
) {

    private val baseStoragePath = "futmatch/users"

    suspend fun getUserById(userId: UUID?, locale: Locale): AppResult<UserResponse> {
        userId ?: return locale.createError(status = HttpStatusCode.NotFound)
        val userBaseInfo: UserBaseInfo = dbExecutor.tx { userRepository.getUserById(userId) }
            ?: return locale.createError(status = HttpStatusCode.NotFound)

        // Generate signed URL for profile pic if exists
        val profilePicUrl = userBaseInfo.profilePic?.let { fileName ->
            val publicId = "$baseStoragePath/$userId/$fileName"
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

        val path = "$baseStoragePath/$userId"
        
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
             val publicId = "$baseStoragePath/$userId/$newImageName"
             imageService.deleteImages(publicId)
             
             return locale.createError(
                titleKey = StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                descriptionKey = StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY,
                status = HttpStatusCode.InternalServerError
            )
        }

        // Delete old image if exists
        if (!currentUser.profilePic.isNullOrBlank()) {
            val oldPublicId = "$baseStoragePath/$userId/${currentUser.profilePic}"
            imageService.deleteImages(oldPublicId)
        }

        return AppResult.Success(
            data = locale.getString(StringResourcesKey.IMAGE_UPLOAD_SUCCESS_MESSAGE),
            appStatus = HttpStatusCode.OK
        )
    }
}
