package com.devapplab.service.field

import com.devapplab.data.repository.FieldRepository
import com.devapplab.model.AppResult
import com.devapplab.model.ErrorCode
import com.devapplab.model.field.Field
import com.devapplab.model.field.FieldImage
import com.devapplab.model.field.mapper.toResponse
import com.devapplab.model.field.response.FieldResponse
import com.devapplab.model.field.response.FieldWithImagesResponse
import com.devapplab.service.image.ImageService
import com.devapplab.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import java.util.*

class FieldService(
    private val fieldRepository: FieldRepository,
    private val imageService: ImageService,
) {

    private val maxImagesPerField = 4
    private val baseStoragePath = "futmatch/fields"

    suspend fun createField(field: Field): AppResult<FieldResponse> {
        val fielResponse = fieldRepository.createField(field).toResponse()
        return AppResult.Success(data = fielResponse)
    }

    suspend fun saveFieldImage(
        locale: Locale,
        fieldId: UUID,
        position: Int,
        multiPartData: MultiPartData
    ): AppResult<UUID> {
        val alreadyExists = fieldRepository.existsFieldImageAtPosition(fieldId, position)
        if (alreadyExists) {
            return locale.createError(
                titleKey = StringResourcesKey.FIELD_IMAGE_POSITION_EXISTS_TITLE,
                descriptionKey = StringResourcesKey.FIELD_IMAGE_POSITION_EXISTS_DESCRIPTION,
                errorCode = ErrorCode.ALREADY_EXISTS
            )
        }

        val imagesCount = fieldRepository.getImagesCountByField(fieldId)
        if (imagesCount == maxImagesPerField) {
            return locale.createError(
                titleKey = StringResourcesKey.FIELD_MAX_IMAGES_REACHED_TITLE,
                descriptionKey = StringResourcesKey.FIELD_MAX_IMAGES_REACHED_DESCRIPTION,
                errorCode = ErrorCode.GENERAL_ERROR
            )
        }

        // Storage folder structure
        val path = "$baseStoragePath/$fieldId"
        val imageSaved = imageService.saveImages(multiPartData, path).first()

        // We only store the filename (last part of public_id) in the DB
        val filename = imageSaved.imageName.substringAfterLast('/')

        val fieldImage = FieldImage(
            fieldId = fieldId,
            key = filename, 
            position = position,
            mime = imageSaved.imageMeta.mime,
            sizeBytes = imageSaved.imageMeta.sizeBytes,
            width = imageSaved.imageMeta.width,
            height = imageSaved.imageMeta.height,
        )

        val imageId = fieldRepository.createImageField(fieldImage)

        return AppResult.Success(imageId, appStatus = HttpStatusCode.Created)
    }

    suspend fun getImage(
        locale: Locale,
        imageName: String?
    ): AppResult<String> {

        val image = fieldRepository.getImageByKey(imageName ?: "")
            ?: return locale.createError(
                titleKey = StringResourcesKey.FIELD_IMAGE_NOT_FOUND_TITLE,
                descriptionKey = StringResourcesKey.FIELD_IMAGE_NOT_FOUND_DESCRIPTION,
                errorCode = ErrorCode.NOT_FOUND
            )

        // Reconstruct the full storage path
        val publicId = "$baseStoragePath/${image.fieldId}/${image.key}"
        val imageUrl = imageService.getImageUrl(publicId)

        return AppResult.Success(data = imageUrl)
    }

    suspend fun updateFieldImage(
        locale: Locale,
        imageId: UUID,
        multiPartData: MultiPartData,
    ): AppResult<UUID> {
        val currentFieldImage = fieldRepository.getImageById(imageId) ?: return locale.createError()
        
        val path = "$baseStoragePath/${currentFieldImage.fieldId}"
        val imageSaved = imageService.saveImages(multiPartData, path).first()
        
        val filename = imageSaved.imageName.substringAfterLast('/')

        val fieldImage = FieldImage(
            imageId = imageId,
            fieldId = currentFieldImage.fieldId,
            key = filename,
            position = currentFieldImage.position,
            mime = imageSaved.imageMeta.mime,
            sizeBytes = imageSaved.imageMeta.sizeBytes,
            width = imageSaved.imageMeta.width,
            height = imageSaved.imageMeta.height,
        )

        val updated = fieldRepository.updateImageField(fieldImage, imageId)

        if (updated) {
            val oldPublicId = "$baseStoragePath/${currentFieldImage.fieldId}/${currentFieldImage.key}"
            imageService.deleteImages(oldPublicId)
        }

        return AppResult.Success(imageId, appStatus = HttpStatusCode.Created)
    }


    suspend fun deleteFieldImage(
        locale: Locale,
        imageId: UUID,
    ): AppResult<String> {

        val currentFieldImage = fieldRepository.getImageById(imageId)
            ?: return locale.createError(
                titleKey = StringResourcesKey.IMAGE_NOT_FOUND_TITLE,
                descriptionKey = StringResourcesKey.IMAGE_NOT_FOUND_DESCRIPTION
            )

        val deleted = fieldRepository.deleteImageField(imageId)

        if (!deleted) {
            return locale.createError(
                titleKey = StringResourcesKey.IMAGE_DELETE_FAILED_TITLE,
                descriptionKey = StringResourcesKey.IMAGE_DELETE_FAILED_DESCRIPTION
            )
        }

        val publicId = "$baseStoragePath/${currentFieldImage.fieldId}/${currentFieldImage.key}"
        imageService.deleteImages(publicId)

        return AppResult.Success(
            data = locale.getString(StringResourcesKey.IMAGE_DELETE_SUCCESS_MESSAGE),
            appStatus = HttpStatusCode.OK
        )
    }

    suspend fun ensureAdminAssignedToField(adminId: UUID, fieldId: UUID) {
        val allowed = fieldRepository.isAdminAssignedToField(adminId, fieldId)
        if (!allowed) throw AccessDeniedException()
    }

    suspend fun updateField(locale: Locale, field: Field): AppResult<Boolean> {
        val fieldId = field.id ?: return locale.createError(
            titleKey = StringResourcesKey.FIELD_UPDATE_FAILED_TITLE,
            descriptionKey = StringResourcesKey.FIELD_UPDATE_FAILED_DESCRIPTION,
            errorCode = ErrorCode.GENERAL_ERROR,
            status = HttpStatusCode.BadRequest
        )

        val updated = fieldRepository.updateField(fieldId, field)
        return if (updated) {
            AppResult.Success(true)
        } else {
            locale.createError(
                titleKey = StringResourcesKey.FIELD_UPDATE_FAILED_TITLE,
                descriptionKey = StringResourcesKey.FIELD_UPDATE_FAILED_DESCRIPTION,
                errorCode = ErrorCode.GENERAL_ERROR,
                status = HttpStatusCode.BadRequest
            )
        }
    }

    suspend fun linkLocationToField(locale: Locale, fieldId: UUID, locationId: UUID): AppResult<Boolean> {
        val updated = fieldRepository.updateFieldLocation(fieldId, locationId)
        return if (updated) {
            AppResult.Success(true)
        } else {
            locale.createError(
                titleKey = StringResourcesKey.FIELD_UPDATE_FAILED_TITLE,
                descriptionKey = StringResourcesKey.FIELD_UPDATE_FAILED_DESCRIPTION,
                errorCode = ErrorCode.GENERAL_ERROR,
                status = HttpStatusCode.BadRequest
            )
        }
    }

    suspend fun deleteField(
        locale: Locale,
        fieldId: UUID,
    ): AppResult<String> {
        val wasDeleted = fieldRepository.deleteField(fieldId)

        if (!wasDeleted) {
            return locale.createError(
                titleKey = StringResourcesKey.FIELD_DELETE_ACCESS_DENIED_TITLE,
                descriptionKey = StringResourcesKey.FIELD_DELETE_ACCESS_DENIED_DESCRIPTION,
                status = HttpStatusCode.Unauthorized,
                errorCode = ErrorCode.ACCESS_DENIED
            )
        }
        
        // Note: We are not deleting the folder in storage here because it requires
        // the folder to be empty or using the Admin API which has rate limits.
        // The images are effectively orphaned but won't be accessed.
        // A background job could clean them up if needed.

        return AppResult.Success(
            locale.getString(StringResourcesKey.FIELD_DELETE_SUCCESS_MESSAGE),
            appStatus = HttpStatusCode.OK
        )
    }

    suspend fun getFieldsByAdminId(adminId: UUID): AppResult<List<FieldWithImagesResponse>> {
        val fields = fieldRepository.getFieldsByAdminId(adminId).map { it.toResponse() }
        return AppResult.Success(fields)
    }
}