package com.devapplab.service.field

import com.devapplab.data.repository.FieldRepository
import com.devapplab.model.AppResult
import com.devapplab.model.ErrorCode
import com.devapplab.model.field.Field
import com.devapplab.model.field.FieldImage
import com.devapplab.model.field.mapper.toResponse
import com.devapplab.model.field.response.FieldResponse
import com.devapplab.model.field.response.FieldWithImagesResponse
import com.devapplab.model.image.ImageFileInfo
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

        val path = createImagePath(fieldId)
        val imageSaved = imageService.saveImages(multiPartData, path).first()

        val fieldImage = FieldImage(
            fieldId = fieldId,
            key = imageSaved.imageName,
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
        fieldId: UUID?,
        imageName: String?
    ): AppResult<ImageFileInfo> {

        val image = fieldRepository.getImageByKey(imageName ?: "")
            ?: return locale.createError(
                titleKey = StringResourcesKey.FIELD_IMAGE_NOT_FOUND_TITLE,
                descriptionKey = StringResourcesKey.FIELD_IMAGE_NOT_FOUND_DESCRIPTION,
                errorCode = ErrorCode.NOT_FOUND
            )

        val file = getFileFromImageMeta(fieldId, imageName, image.mime)


        if (!file.exists()) {
            return locale.createError(
                titleKey = StringResourcesKey.FIELD_IMAGE_FILE_MISSING_TITLE,
                descriptionKey = StringResourcesKey.FIELD_IMAGE_FILE_MISSING_DESCRIPTION,
                errorCode = ErrorCode.NOT_FOUND
            )
        }

        val mimeType = ContentType.defaultForFile(file)

        return AppResult.Success(data = ImageFileInfo(file, mimeType))
    }

    suspend fun updateFieldImage(
        locale: Locale,
        imageId: UUID,
        multiPartData: MultiPartData,
    ): AppResult<UUID> {
        val currentFieldImage = fieldRepository.getImageById(imageId) ?: return locale.createError()
        val filePath = getFileFromImageMeta(currentFieldImage.fieldId, currentFieldImage.key, currentFieldImage.mime)
        val path = createImagePath(currentFieldImage.fieldId)
        val imageSaved = imageService.saveImages(multiPartData, path).first()

        val fieldImage = FieldImage(
            imageId = imageId,
            fieldId = currentFieldImage.fieldId,
            key = imageSaved.imageName,
            position = currentFieldImage.position,
            mime = imageSaved.imageMeta.mime,
            sizeBytes = imageSaved.imageMeta.sizeBytes,
            width = imageSaved.imageMeta.width,
            height = imageSaved.imageMeta.height,
        )

        val updated = fieldRepository.updateImageField(fieldImage, imageId)

        if (updated) {
            imageService.deleteImages(filePath.absolutePath)
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

        val filePath = getFileFromImageMeta(
            currentFieldImage.fieldId,
            currentFieldImage.key,
            currentFieldImage.mime
        )

        val deleted = fieldRepository.deleteImageField(imageId)

        if (!deleted) {
            return locale.createError(
                titleKey = StringResourcesKey.IMAGE_DELETE_FAILED_TITLE,
                descriptionKey = StringResourcesKey.IMAGE_DELETE_FAILED_DESCRIPTION
            )
        }

        if (filePath.exists()) {
            imageService.deleteImages(filePath.absolutePath)
        }

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

        val fieldDir = getFieldDirectory(fieldId)
        if (fieldDir.exists()) {
            fieldDir.deleteRecursively()
        }
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